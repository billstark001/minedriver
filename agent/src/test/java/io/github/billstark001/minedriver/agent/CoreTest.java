package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.protocol.Json;
import io.github.billstark001.minedriver.protocol.RpcClient;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreTest {
  @TempDir Path temporary;

  public static final class Overloaded {
    public String value(Number value) {
      return "number";
    }

    public String value(Integer value) {
      return "integer";
    }

    public int primitive(int value) {
      return value + 1;
    }
  }

  @Test
  void reflectionRejectsAmbiguousSignatures() {
    var object = new Overloaded();
    assertEquals(2, Reflect.call(object, "primitive", 1));
    assertEquals(
        "UNSUPPORTED_API",
        assertThrows(DriverException.class, () -> Reflect.call(object, "value", 1)).code());
  }

  @Test
  void rpcAuthenticationMalformedEnvelopeAndErrors() throws Exception {
    var registry = new CommandRegistry();
    registry.register("test.echo", "echo", true, "read", p -> p);
    try (var server = new RpcServer(registry, "unit-test")) {
      var client = new RpcClient(server.connection(), Duration.ofSeconds(5));
      assertEquals(
          7, client.call("test.echo", Map.of("n", 7)).getAsJsonObject().get("n").getAsInt());
      assertEquals(
          "UNKNOWN_COMMAND",
          assertThrows(RpcClient.RpcException.class, () -> client.call("test.missing", Map.of()))
              .data()
              .getAsJsonObject()
              .get("code")
              .getAsString());
      HttpClient http = HttpClient.newHttpClient();
      URI uri = URI.create(server.connection().endpoint() + "/rpc");
      assertEquals(
          401,
          http.send(
                  HttpRequest.newBuilder(uri)
                      .POST(HttpRequest.BodyPublishers.ofString("{}"))
                      .build(),
                  HttpResponse.BodyHandlers.ofString())
              .statusCode());
      var bad =
          http.send(
              HttpRequest.newBuilder(uri)
                  .header("Authorization", "Bearer " + server.connection().token())
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"jsonrpc\":{},\"method\":null,\"id\":1}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(
          -32600,
          com.google.gson.JsonParser.parseString(bad.body())
              .getAsJsonObject()
              .getAsJsonObject("error")
              .get("code")
              .getAsInt());
    }
  }

  @Test
  void pixelComparisonMasksToleranceAndDifferenceArtifact() throws Exception {
    var expected = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    var actual = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    actual.setRGB(1, 1, 0xffffffff);
    Path a = temporary.resolve("actual.png"), e = temporary.resolve("expected.png");
    ImageIO.write(actual, "png", a.toFile());
    ImageIO.write(expected, "png", e.toFile());
    var p =
        new java.util.HashMap<String, Object>(
            Map.of("actual", a.toString(), "expected", e.toString(), "name", "diff"));
    assertEquals(
        "VISUAL_MISMATCH",
        assertThrows(DriverException.class, () -> VisualComparison.compare(p, temporary)).code());
    assertTrue(java.nio.file.Files.isRegularFile(temporary.resolve("diffs/diff.png")));
    p.put("masks", java.util.List.of(Map.of("x", 1, "y", 1, "width", 1, "height", 1)));
    assertTrue((Boolean) ((Map<?, ?>) VisualComparison.compare(p, temporary)).get("passed"));
  }

  @Test
  void completeReportCarriesIdentityAndFailureXml() throws Exception {
    Path config = temporary.resolve("config.json");
    Json.write(
        config,
        Map.of("protocol", 1, "runId", "report-test", "outputDirectory", temporary.toString()));
    new RunReport(AgentConfig.read(config))
        .finish(new DriverException("ASSERTION_FAILED", "<bad & value>"), Map.of());
    var result = Json.read(temporary.resolve("result.json")).getAsJsonObject();
    assertTrue(result.get("complete").getAsBoolean());
    assertEquals("FAIL", result.get("status").getAsString());
    assertEquals("report-test", result.get("runId").getAsString());
    var document =
        javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(temporary.resolve("junit.xml").toFile());
    assertEquals(1, document.getElementsByTagName("failure").getLength());
  }
}
