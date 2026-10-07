package uk.gov.defra.cdp.dynamicsgateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;

class ExternalCallMetricsTest {

  private static final String NAMESPACE = "trade-imports-dynamics-gateway";

  private final CapturingEmfEnvironment environment = new CapturingEmfEnvironment();
  private final ExternalCallMetrics metrics =
      new ExternalCallMetrics(true, NAMESPACE, () -> new MetricsLogger(environment));

  @Test
  void measure_shouldRunTheActionAndEmitOneSuccessDocument() {
    var ran = new AtomicInteger();

    metrics.measure(ExternalCall.SERVICE_BUS_SEND_MESSAGE, ran::incrementAndGet);

    assertThat(ran).hasValue(1);
    assertThat(environment.documents()).hasSize(1);
    JsonNode document = environment.documents().getFirst();
    JsonNode directive = document.get("_aws").get("CloudWatchMetrics").get(0);
    assertThat(directive.get("Namespace").asText()).isEqualTo(NAMESPACE);
    assertThat(directive.get("Dimensions")).hasSize(1);
    assertThat(directive.get("Dimensions").get(0)).hasSize(2);
    assertThat(directive.get("Dimensions").get(0).get(0).asText()).isEqualTo("Dependency");
    assertThat(directive.get("Dimensions").get(0).get(1).asText()).isEqualTo("Operation");
    assertThat(directive.get("Metrics").get(0).get("Name").asText())
        .isEqualTo("ExternalCallDuration");
    assertThat(directive.get("Metrics").get(0).get("Unit").asText()).isEqualTo("Milliseconds");
    assertThat(directive.get("Metrics").get(1).get("Name").asText())
        .isEqualTo("ExternalCallFailure");
    assertThat(directive.get("Metrics").get(1).get("Unit").asText()).isEqualTo("Count");
    assertThat(document.get("Dependency").asText()).isEqualTo("azure-service-bus");
    assertThat(document.get("Operation").asText()).isEqualTo("send-message");
    assertThat(document.get("ExternalCallDuration").asDouble()).isGreaterThanOrEqualTo(0);
    assertThat(document.get("ExternalCallFailure").asInt()).isZero();
    assertThat(document.get("Outcome").asText()).isEqualTo("success");
    assertThat(document.has("Interface")).isFalse();
    assertThat(document.has("LogGroup")).isFalse();
    assertThat(document.has("ServiceName")).isFalse();
    assertThat(document.has("ServiceType")).isFalse();
  }

  @Test
  void record_shouldEmitTheDurationInMilliseconds() {
    metrics.recordCall(ExternalCall.SERVICE_BUS_SEND_MESSAGE, Duration.ofMillis(250), false);

    assertThat(environment.documents().getFirst().get("ExternalCallDuration").asDouble())
        .isEqualTo(250.0);
  }

  @Test
  void measure_shouldRecordTheElapsedTimeOfTheAction() {
    metrics.measure(
        ExternalCall.SERVICE_BUS_SEND_MESSAGE,
        () -> {
          long until = System.nanoTime() + Duration.ofMillis(50).toNanos();
          while (System.nanoTime() < until) {
            Thread.onSpinWait();
          }
        });

    assertThat(environment.documents().getFirst().get("ExternalCallDuration").asDouble())
        .isGreaterThanOrEqualTo(50.0);
  }

  @Test
  void measure_shouldRethrowTheSameRuntimeException_andRecordAFailure() {
    IllegalStateException failure = new IllegalStateException("boom");

    assertThatThrownBy(
            () ->
                metrics.measure(
                    ExternalCall.SERVICE_BUS_SEND_MESSAGE,
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);

    JsonNode document = environment.documents().getFirst();
    assertThat(document.get("ExternalCallFailure").asInt()).isEqualTo(1);
    assertThat(document.get("Outcome").asText()).isEqualTo("failure");
  }

  @Test
  void record_shouldNotThrow_whenTheSinkFails() {
    var failingEnvironment = new CapturingEmfEnvironment(true);
    var failingMetrics =
        new ExternalCallMetrics(true, NAMESPACE, () -> new MetricsLogger(failingEnvironment));

    assertThatCode(
            () ->
                failingMetrics.recordCall(
                    ExternalCall.SERVICE_BUS_SEND_MESSAGE, Duration.ofMillis(5), false))
        .doesNotThrowAnyException();
  }

  @Test
  void record_shouldEmitAnInterface_whenTheCallHasOne() {
    metrics.recordCall(new ExternalCall("mdm", "get-countries", "SYN-19"), Duration.ofMillis(5), false);

    assertThat(environment.documents().getFirst().get("Interface").asText()).isEqualTo("SYN-19");
  }

  @Test
  void record_shouldEmitNothing_whenEmfIsDisabled() {
    var suppliedLoggers = new AtomicInteger();
    var disabled =
        new ExternalCallMetrics(
            false,
            NAMESPACE,
            () -> {
              suppliedLoggers.incrementAndGet();
              return new MetricsLogger(environment);
            });

    disabled.recordCall(ExternalCall.SERVICE_BUS_SEND_MESSAGE, Duration.ofMillis(5), false);

    assertThat(environment.documents()).isEmpty();
    assertThat(suppliedLoggers).hasValue(0);
  }
}
