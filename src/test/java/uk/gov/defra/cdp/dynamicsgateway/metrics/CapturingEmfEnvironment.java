package uk.gov.defra.cdp.dynamicsgateway.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.cloudwatchlogs.emf.environment.Environment;
import software.amazon.cloudwatchlogs.emf.model.MetricsContext;
import software.amazon.cloudwatchlogs.emf.sinks.ISink;

/**
 * A test-only EMF environment whose sink keeps every document it is given, or fails on demand.
 */
public class CapturingEmfEnvironment implements Environment {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<String> serialised = new ArrayList<>();
  private final boolean failing;

  public CapturingEmfEnvironment() {
    this(false);
  }

  public CapturingEmfEnvironment(boolean failing) {
    this.failing = failing;
  }

  /** Returns every document the sink has accepted, parsed. */
  public List<JsonNode> documents() {
    return serialised.stream().map(CapturingEmfEnvironment::parse).toList();
  }

  private static JsonNode parse(String document) {
    try {
      return OBJECT_MAPPER.readTree(document);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean probe() {
    return true;
  }

  @Override
  public String getName() {
    return "test";
  }

  @Override
  public String getType() {
    return "test";
  }

  @Override
  public String getLogGroupName() {
    return "test";
  }

  @Override
  public void configureContext(MetricsContext context) {
    // Nothing to add: the default dimensions are not wanted in a captured document.
  }

  @Override
  public ISink getSink() {
    return new ISink() {
      @Override
      public void accept(MetricsContext context) {
        if (failing) {
          throw new IllegalStateException("sink unavailable");
        }
        try {
          serialised.addAll(context.serialize());
        } catch (JsonProcessingException e) {
          throw new IllegalStateException(e);
        }
      }

      @Override
      public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
      }
    };
  }
}
