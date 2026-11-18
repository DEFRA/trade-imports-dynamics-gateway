package uk.gov.defra.cdp.dynamicsgateway.testsupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

public final class CapturedConsole implements AutoCloseable {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;

  private CapturedConsole() {
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
  }

  public static CapturedConsole captureSystemOut() {
    return new CapturedConsole();
  }

  public String text() {
    return captured.toString(StandardCharsets.UTF_8);
  }

  /**
   * The first complete JSON line written so far that matches. A line still being written is left
   * out, so a caller polling while the console is written to never parses half a line.
   *
   * @param matches which line to find
   * @return the line, parsed, or empty when no complete line matches
   */
  public Optional<JsonNode> firstJsonLine(Predicate<JsonNode> matches) {
    String text = text();
    String completeLines = text.substring(0, text.lastIndexOf('\n') + 1);
    return Arrays.stream(completeLines.split("\n"))
        .filter(line -> line.trim().startsWith("{"))
        .map(CapturedConsole::parsed)
        .filter(matches)
        .findFirst();
  }

  @Override
  public void close() {
    System.setOut(originalOut);
  }

  private static JsonNode parsed(String line) {
    try {
      return JSON.readTree(line);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
