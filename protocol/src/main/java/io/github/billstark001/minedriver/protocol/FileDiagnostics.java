package io.github.billstark001.minedriver.protocol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Evidence only: never changes ownership, ACLs or permissions. Each process writes its own file.
 */
public final class FileDiagnostics {
  private final Path file;
  private final Map<String, Object> process = new LinkedHashMap<>();
  private final List<Map<String, Object>> observations = new ArrayList<>();

  public FileDiagnostics(Path output, String role) {
    file = output.resolve("filesystem-" + role + ".json");
    ProcessHandle handle = ProcessHandle.current();
    process.put("role", role);
    process.put("pid", handle.pid());
    process.put("parentPid", handle.parent().map(ProcessHandle::pid).orElse(null));
    process.put("javaUser", System.getProperty("user.name"));
    process.put("javaHome", System.getProperty("java.home"));
    process.put("workingDirectory", Path.of("").toAbsolutePath().normalize().toString());
    if (System.getProperty("os.name").startsWith("Windows")) {
      Process probe = null;
      try {
        // The child inherits this process's primary token. No paths, secrets or user text enter the
        // script.
        String script =
            "$i=[Security.Principal.WindowsIdentity]::GetCurrent(); "
                + "$p=[Security.Principal.WindowsPrincipal]::new($i); "
                + "@{userSid=$i.User.Value;defaultOwnerSid=$i.Owner.Value;name=$i.Name;"
                + "administratorEnabled=$p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)}"
                + "|ConvertTo-Json -Compress";
        probe =
            new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-WindowStyle",
                    "Hidden",
                    "-Command",
                    script)
                .redirectErrorStream(true)
                .start();
        if (!probe.waitFor(3, TimeUnit.SECONDS))
          throw new java.io.IOException("Token probe timed out");
        String value =
            new String(probe.getInputStream().readNBytes(16384), StandardCharsets.UTF_8).trim();
        if (probe.exitValue() != 0) throw new java.io.IOException(value);
        process.put("inheritedPrimaryToken", com.google.gson.JsonParser.parseString(value));
      } catch (Exception failure) {
        process.put("tokenDiagnosticError", failure.toString());
        if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
      } finally {
        if (probe != null && probe.isAlive()) probe.destroyForcibly();
      }
    }
  }

  public synchronized void capture(String stage, Path... paths) {
    for (Path input : paths) {
      Path path = input.toAbsolutePath().normalize();
      var observation = new LinkedHashMap<String, Object>();
      observation.put("stage", stage);
      observation.put("time", Instant.now().toString());
      observation.put("path", path.toString());
      observation.put("exists", Files.exists(path, LinkOption.NOFOLLOW_LINKS));
      try {
        observation.put("symbolicLink", Files.isSymbolicLink(path));
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
          observation.put("realPath", path.toRealPath().toString());
          observation.put("owner", Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName());
          var acl =
              Files.getFileAttributeView(
                  path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
          if (acl != null)
            observation.put("acl", acl.getAcl().stream().map(Object::toString).toList());
        }
      } catch (Exception failure) {
        observation.put("diagnosticError", failure.toString());
      }
      observations.add(observation);
    }
  }

  public synchronized void write() {
    try {
      Json.write(file, Map.of("process", process, "observations", observations));
    } catch (Exception failure) {
      System.err.println("MineDriver filesystem diagnostics: " + failure);
    }
  }
}
