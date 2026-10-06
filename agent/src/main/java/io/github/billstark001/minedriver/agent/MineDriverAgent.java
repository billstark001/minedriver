package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.protocol.Json;
import java.lang.instrument.Instrumentation;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Entry point for a launch-time Java agent. No loader metadata or mod initializer is shipped. */
public final class MineDriverAgent {
  private static final AtomicBoolean INSTALLED = new AtomicBoolean();

  private MineDriverAgent() {}

  public static void premain(String arguments, Instrumentation instrumentation) {
    start(arguments, instrumentation);
  }

  public static void agentmain(String arguments, Instrumentation instrumentation) {
    start(arguments, instrumentation);
  }

  private static void start(String arguments, Instrumentation instrumentation) {
    if (!INSTALLED.compareAndSet(false, true))
      throw new IllegalStateException("MineDriver is already installed");
    try {
      String file =
          arguments == null || arguments.isBlank()
              ? System.getProperty("minedriver.config")
              : arguments;
      if (file == null)
        throw new IllegalArgumentException("Pass -javaagent:agent.jar=absolute/config.json");
      AgentConfig config = AgentConfig.read(Path.of(file));
      Files.createDirectories(config.output());
      FileChannel channel =
          FileChannel.open(
              config.output().resolve("session.lock"),
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE);
      FileLock lock = channel.tryLock();
      if (lock == null || Files.exists(config.resultFile())) {
        channel.close();
        throw new IllegalStateException("Session output must be unused: " + config.output());
      }
      var report = new RunReport(config);
      FrameTransformer frames;
      try {
        frames = BootstrapHooks.install(instrumentation, config.output());
      } catch (Throwable failure) {
        report.finish(failure, Map.of());
        lock.close();
        channel.close();
        throw failure;
      }
      var commands = new CommandRegistry();
      commands.report(report);
      var state = new java.util.concurrent.atomic.AtomicReference<String>("STARTING");
      commands.register(
          "agent.status",
          "Inspect agent startup without waiting for the game",
          true,
          "read",
          p -> Map.of("state", state.get(), "runId", config.runId()));
      var rpc = new RpcServer(commands, config.runId());
      Json.write(config.connectionFile(), rpc.connection());
      var finished = new AtomicBoolean();
      Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
      Thread.setDefaultUncaughtExceptionHandler(
          (thread, error) -> {
            if (previousHandler != null) previousHandler.uncaughtException(thread, error);
            else error.printStackTrace(System.err);
            if ((thread.getName().equals("main") || thread.getName().equals("Render thread"))
                && finished.compareAndSet(false, true)) {
              try {
                report.finish(
                    new DriverException("CLIENT_CRASHED", "Uncaught client exception", error),
                    Map.of());
              } catch (Exception reportError) {
                reportError.printStackTrace(System.err);
              }
              rpc.close();
              Thread exit =
                  new Thread(
                      () -> {
                        try {
                          Thread.sleep(5000);
                        } catch (InterruptedException interrupted) {
                          Thread.currentThread().interrupt();
                        }
                        Runtime.getRuntime().halt(72);
                      },
                      "MineDriver crash deadline");
              exit.setDaemon(true);
              exit.start();
            }
          });
      Thread shutdown =
          new Thread(
              () -> {
                if (finished.compareAndSet(false, true)) {
                  try {
                    report.finish(
                        new DriverException(
                            "CLIENT_EXITED", "Client exited before the session completed"),
                        Map.of());
                  } catch (Exception failure) {
                    failure.printStackTrace(System.err);
                  }
                }
                rpc.close();
              },
              "MineDriver shutdown");
      Runtime.getRuntime().addShutdownHook(shutdown);
      Thread worker =
          new Thread(
              () -> {
                GameRuntime game = null;
                Throwable failure = null;
                try {
                  long deadline =
                      System.nanoTime()
                          + Duration.ofMillis(
                                  config.timeoutMillis() == 0 ? 300_000 : config.timeoutMillis())
                              .toNanos();
                  Object minecraft = null;
                  while (minecraft == null) {
                    if (frames.failure() != null)
                      throw new DriverException(
                          "UNSUPPORTED_API", "Frame hook failed: " + frames.failure());
                    // A completed frame proves Minecraft's class and instance initialization
                    // finished on the game thread; never initialize its class from this worker.
                    if (io.github.billstark001.minedriver.hooks.FrameClock.count() > 0)
                      for (Class<?> type : instrumentation.getAllLoadedClasses())
                        if (type.getName().equals("net.minecraft.client.Minecraft")) {
                          minecraft = Reflect.call(type, "getInstance");
                          break;
                        }
                    if (System.nanoTime() >= deadline)
                      throw new DriverException(
                          "STARTUP_TIMEOUT", "Minecraft instance did not become available");
                    if (minecraft == null) GameRuntime.pause();
                  }
                  game = new GameRuntime(minecraft, config, frames);
                  try (var session = new DriverSession(config, game, commands, report)) {
                    session.ready();
                    state.set("READY");
                    if (config.mode().equals("check"))
                      session.run(config.plan(), config.scenarios());
                    else
                      while (!session.isClosing()) {
                        GameRuntime.pause();
                      }
                    session.requireSuccessfulScenario();
                  }
                } catch (Throwable error) {
                  failure = error;
                  state.set("FAILED");
                } finally {
                  if (failure != null && game != null) {
                    GameRuntime captured = game;
                    try {
                      Json.write(
                          config.output().resolve("failure-ui.json"),
                          captured.client(() -> captured.ui.tree(8192), Duration.ofSeconds(3)));
                    } catch (Exception capture) {
                      failure.addSuppressed(capture);
                    }
                    try {
                      game.media.screenshot(Map.of("name", "failure"), Duration.ofSeconds(3));
                    } catch (Exception capture) {
                      failure.addSuppressed(capture);
                    }
                  }
                  if (finished.compareAndSet(false, true)) {
                    try {
                      report.finish(
                          failure,
                          game == null
                              ? Map.of()
                              : game.client(game::inspect, Duration.ofSeconds(3)));
                    } catch (Throwable reportFailure) {
                      reportFailure.printStackTrace(System.err);
                      try {
                        report.finish(reportFailure, Map.of());
                      } catch (Exception ignored) {
                        ignored.printStackTrace(System.err);
                      }
                    }
                  }
                  state.set(failure == null ? "FINISHED" : "FAILED");
                  rpc.close();
                  if (game != null) {
                    try {
                      GameRuntime client = game;
                      client.client(
                          () -> {
                            Reflect.call(client.minecraft, "stop");
                            return null;
                          },
                          Duration.ofSeconds(3));
                    } catch (Throwable stopFailure) {
                      stopFailure.printStackTrace(System.err);
                    }
                  }
                  try {
                    lock.close();
                    channel.close();
                  } catch (Exception closeFailure) {
                    closeFailure.printStackTrace(System.err);
                  }
                  // Only this launched client is terminated; the Gradle daemon is a different
                  // process.
                  Thread exit =
                      new Thread(
                          () -> {
                            try {
                              Thread.sleep(15_000);
                            } catch (InterruptedException ignored) {
                              Thread.currentThread().interrupt();
                            }
                            Runtime.getRuntime().halt(70);
                          },
                          "MineDriver exit deadline");
                  exit.setDaemon(true);
                  exit.start();
                }
              },
              "MineDriver lifecycle");
      worker.setDaemon(true);
      worker.start();
      if (config.timeoutMillis() > 0) {
        Thread watchdog =
            new Thread(
                () -> {
                  try {
                    Thread.sleep(config.timeoutMillis());
                  } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                  }
                  if (finished.compareAndSet(false, true)) {
                    try {
                      report.finish(
                          new DriverException(
                              "SESSION_TIMEOUT",
                              "Session exceeded " + config.timeoutMillis() + "ms"),
                          Map.of());
                    } catch (Exception error) {
                      error.printStackTrace(System.err);
                    }
                    Runtime.getRuntime().halt(71);
                  }
                },
                "MineDriver watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
      }
    } catch (Throwable failure) {
      System.err.println("MineDriver agent could not start: " + failure);
      failure.printStackTrace(System.err);
      throw Reflect.propagate(failure);
    }
  }
}
