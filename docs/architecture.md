# Architecture and extension contract

## Components

| Module | Responsibility |
| --- | --- |
| `gradle-plugin` | Public DSL; native launch adapters; independent source set; fresh owned JavaExec sessions; embedded immutable agent/API extraction; native report verification. |
| `agent` | Instrumentation entry point, loopback RPC, game-thread dispatch, versioned adapters, scenarios, screenshot/fixture/performance functions and reports. |
| `hooks` | Dependency-free bootstrap-visible frame clock and bounded sample ring. Embedded in the agent, appended to the bootstrap search path at launch. |
| `api` | Loader-neutral `Scenario`, `ProbeContext`, `DriverExtension`, `ExtensionRegistry`, `Selector`, `DriverException`. |
| `protocol` | Connection model, JSON/atomic writes, RPC client and command schemas. |
| `cli` | Local commands/watch and MCP 2025-11-25 stdio tools bridge. |
| `test-support` | Simulated Minecraft-shaped client, used only in tests; not published, embedded in the driver or a compatibility claim. |

The agent is a standalone shaded JAR. ASM and Gson are relocated into MineDriver's internal namespace to avoid Fabric/NeoForge dependency collisions. Minecraft types are resolved using the game's loader; shared API types always come from the agent's copy to avoid scenario class-identity conflicts. Only the frame hook refers to bootstrap-visible helpers, which depend on JDK primitives/collections.

The standalone agent publication uses a dependency-free Maven POM and omits thin-component Gradle module metadata. The plugin/API/protocol publications retain their normal component metadata, and the Gradle plugin marker supports local Maven consumption. Dependency licenses are bundled in JAR resources; the test-support module has no publication.

The transformer matches the exact Minecraft class and supported `(boolean) -> void` frame method. It prefers `renderFrame` when both it and `runTick` exist and is idempotent. The lifecycle worker waits for a completed frame before invoking Minecraft methods, so it never initializes Minecraft's static state on an agent thread.

## Lifecycle and threading

1. Gradle prepares the native launcher/assets and compiles the independent `minedriver` source set.
2. A content-addressed embedded agent is extracted, then an unused run/report directory and run ID are created. Seed files are copied; symlinks are rejected.
3. The agent locks the session output, installs bootstrap hooks and writes a random loopback connection. `agent.status` works during startup.
4. Startup waits for the native title screen and resource overlay completion. Accessibility onboarding can continue automatically. NeoForge loading warnings require explicit `allowStartupWarnings`; fatal loading failures are never treated as success.
5. Check mode executes the plan and scenarios; interactive mode serves commands until `session.close`. Commands run on workers, with game actions serialized by a fair reentrant lock. Status/lifecycle discovery remains available while a long command waits.
6. Short native actions are scheduled on the Minecraft or integrated-server event loop. Workers may wait for conditions/frames; game/server threads must not block waiting for themselves.
7. Completion writes JSON/JUnit XML/HTML, then atomically writes the result marker last. Gradle validates identity, completeness, verdict and process exit.

Shutdown, uncaught main/render-thread failures and the overall watchdog write explicit failure outcomes. On ordinary completion the agent asks Minecraft to stop. If the owned client cannot exit within the fallback deadline, it is halted; a forced/nonzero exit still fails Gradle even if a report exists. Interactive mode defaults to no session deadline, but its individual actions remain bounded. Check mode suppresses Fabric's Swing error dialog so failed mod discovery cannot wait for an unseen confirmation indefinitely.

Cancellation skips queued actions that have not started. It cannot safely interrupt a running game callback. The fair command lock serializes individual operations, not an entire multi-command scenario as a transaction; avoid issuing competing workflows from multiple clients. Custom Java code runs with full process privileges, and extensions must follow the threading contract.

## Public Java API

`Scenario.run(ProbeContext)` runs off the game thread. Use `context.call(...)`, selector helpers and `context.require(...)` for assertions. `context.await(description, timeout, condition)` polls a bounded condition and refuses game-thread waits.

`context.onClient(Callable)` and `context.onServer(Callable)` are for **short, nonblocking** work with typed Minecraft APIs. Do not call commands, wait for render frames, perform long IO or await another game thread from inside them. Prefer returning immutable snapshots rather than retaining live objects. `onServer` requires a local integrated server; it cannot inspect a remote server's private state.

```java
public final class ModProbe implements Scenario {
    @Override public void run(ProbeContext context) {
        boolean available = context.onClient(() -> MyClientController.isReady());
        context.require(available, "Controller must be ready");
        context.screenshot("controller");
    }
}
```

Compile such code in the consuming project's source set; MineDriver does not depend on the mod's classes.

## Custom commands and stable IDs

An extension has a public no-argument constructor and implements `DriverExtension.register`. Only `custom.*` names may be registered, and duplicate names fail startup. Registration runs off the game thread. The handler receives a context and JSON parameters; schedule game access explicitly:

```java
public final class ModExtension implements DriverExtension {
    @Override public void register(ExtensionRegistry registry) {
        registry.register("custom.metrics", "Read mod task state", true,
            (context, parameters) -> context.onClient(() ->
                Map.of("queued", MyClientController.queuedCount())));
    }
}
```

Register with `mineDriver.extensionClasses.add('example.ModExtension')`. The read-only flag describes changes to game state for tools clients; keep it accurate. Custom parameter schemas are generic objects in this version, so document and validate your handler's arguments. A handler must return null, strings, booleans, finite numbers, maps with string keys or iterables of these values. Cycles, live game objects, depth >32 and >8192 values fail with `INVALID_EXTENSION_RESULT`.

`registry.identify(widget, "mod.screen.action")` gives a weakly held, language-independent ID. Register actual widget instances when creating/reinitializing the screen, on the client thread. IDs are queried as `{"stableId":"mod.screen.action"}`. The [ScreenExtension example](../examples/scenarios/ScreenExtension.java) demonstrates IDs, native buttons, semantic activation and input clicks; no production entrypoint is needed.

The unsafe `java.invoke` command is separately opt-in and restricted to explicit public static reflection; ordinary custom Java scenarios already have full code privileges. This is a debugging convenience, not a security boundary.

## Build integration boundaries

The launch adapters currently cover standard JavaExec, Fabric Loom 1.17.11 and NeoForge ModDevGradle 2.0.147. Loader task internals needed for launch are explicitly isolated in `LoaderLaunchAdapters`; unavailable contracts fail rather than guessing another launcher. Native classpath argument files, asset preparation, loader environments and NeoForge deferred classpath providers are retained.

A basic JavaExec project's scenario/JAR build can reuse the configuration cache (tested). Loom driver launches explicitly declare incompatibility because native providers are evaluated during configuration; broader live-launch configuration-cache support is not promised. Profiles inherit global options and override native source task, plan and scenarios. Cross-version adaptation occurs at the game API boundary, not by rewriting the consuming mod.

The public API/package and protocol 1 are small extension boundaries, but this is a pre-release snapshot and future compatibility changes will need migration notes. Adding an older Minecraft version requires explicit mapped class/field/event/frame contracts and real integration tests, rather than relaxing the version guard alone.
