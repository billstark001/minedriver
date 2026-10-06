package io.github.billstark001.minedriver.protocol;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/** A per-session local endpoint; token is never included in report metadata or ordinary logs. */
public record Connection(int protocol, String runId, String endpoint, String token, long pid) {
  public Connection {
    if (protocol != 1) throw new IllegalArgumentException("Unsupported protocol " + protocol);
    URI uri = URI.create(endpoint);
    if (!"http".equals(uri.getScheme())
        || !"127.0.0.1".equals(uri.getHost())
        || uri.getUserInfo() != null)
      throw new IllegalArgumentException("MineDriver connections must use loopback HTTP");
    if (token == null || token.length() < 32)
      throw new IllegalArgumentException("Invalid session token");
  }

  public static Connection read(Path path) throws IOException {
    return Json.GSON.fromJson(Json.read(path), Connection.class);
  }
}
