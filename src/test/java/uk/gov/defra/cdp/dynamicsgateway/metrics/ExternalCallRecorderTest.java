package uk.gov.defra.cdp.dynamicsgateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.CapturedLogs.keyValues;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.CapturedLogs.loggedBy;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.durationCount;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.failureTotal;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.recordedCallCount;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.totalDurationMillis;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ExternalCallRecorderTest {

  private static final ExternalCall SEND = ExternalCall.SERVICE_BUS_SEND_MESSAGE;
  private static final ExternalCall RECEIVE = new ExternalCall(Dependency.SQS, "receive-message");

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final LastCallOutcomes lastCallOutcomes = new LastCallOutcomes();
  private final AtomicLong nanoTime = new AtomicLong();
  private final ExternalCallRecorder recorder =
      new ExternalCallRecorder(meterRegistry, lastCallOutcomes, nanoTime::get);

  @Test
  void recordSuccess_shouldRecordTheDurationAZeroFailureAndTheDependencyAsSucceeded() {
    recorder.recordSuccess(SEND, Duration.ofMillis(7));

    assertThat(durationCount(meterRegistry, SEND)).isEqualTo(1);
    assertThat(totalDurationMillis(meterRegistry, SEND))
        .isEqualTo(7);
    assertThat(recordedCallCount(meterRegistry, SEND)).isEqualTo(1);
    assertThat(failureTotal(meterRegistry, SEND)).isZero();
    assertThat(lastCallOutcomes.lastCallSucceeded(Dependency.AZURE_SERVICE_BUS)).contains(true);
  }

  @Test
  void recordFailure_shouldRecordTheDurationAFailureOfOneAndTheDependencyAsFailed() {
    recorder.recordFailure(RECEIVE, Duration.ofMillis(3), new IllegalStateException());

    assertThat(durationCount(meterRegistry, RECEIVE)).isEqualTo(1);
    assertThat(totalDurationMillis(meterRegistry, RECEIVE)).isEqualTo(3);
    assertThat(recordedCallCount(meterRegistry, RECEIVE)).isEqualTo(1);
    assertThat(failureTotal(meterRegistry, RECEIVE)).isEqualTo(1);
    assertThat(lastCallOutcomes.lastCallSucceeded(Dependency.SQS)).contains(false);
  }

  @Test
  void recordSuccess_shouldLogOneInfoLineWithTheEventFieldsAndANanosecondDuration() {
    List<ILoggingEvent> lines =
        loggedBy(ExternalCallRecorder.class, () -> recorder.recordSuccess(SEND, Duration.ofMillis(2)));

    assertThat(lines).hasSize(1);
    ILoggingEvent line = lines.getFirst();
    assertThat(line.getLevel()).isEqualTo(Level.INFO);
    assertThat(line.getFormattedMessage())
        .isEqualTo("External call azure-service-bus/send-message success in 2 ms");
    assertThat(keyValues(line))
        .containsEntry("event.category", "external-call")
        .containsEntry("event.action", "azure-service-bus/send-message")
        .containsEntry("event.outcome", "success")
        .containsEntry("event.duration", 2_000_000L);
    assertThat(line.getThrowableProxy()).isNull();
  }

  @Test
  void recordFailure_shouldLogOneWarnLineWithTheErrorTypeAndMessageButNoStackTrace() {
    IllegalStateException cause = new IllegalStateException("The sender is disposed");

    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class,
            () -> recorder.recordFailure(SEND, Duration.ofMillis(4), cause));

    assertThat(lines).hasSize(1);
    ILoggingEvent line = lines.getFirst();
    assertThat(line.getLevel()).isEqualTo(Level.WARN);
    assertThat(keyValues(line))
        .containsEntry("event.action", "azure-service-bus/send-message")
        .containsEntry("event.outcome", "failure")
        .containsEntry("error.type", "java.lang.IllegalStateException")
        .containsEntry("error.message", "The sender is disposed");
    assertThat(line.getThrowableProxy()).isNull();
  }

  @Test
  void measure_shouldRecordTheTimeTheCallTookAsASuccess_whenTheCallReturns() {
    recorder.measure(SEND, () -> nanoTime.addAndGet(Duration.ofMillis(50).toNanos()));

    assertThat(durationCount(meterRegistry, SEND)).isEqualTo(1);
    assertThat(totalDurationMillis(meterRegistry, SEND))
        .isEqualTo(50);
    assertThat(failureTotal(meterRegistry, SEND)).isZero();
    assertThat(lastCallOutcomes.lastCallSucceeded(Dependency.AZURE_SERVICE_BUS)).contains(true);
  }

  @Test
  void measure_shouldRecordTheTimeTheCallTookAsAFailureAndRethrow_whenTheCallThrows() {
    IllegalStateException cause = new IllegalStateException("The sender is disposed");

    assertThatThrownBy(
            () ->
                recorder.measure(
                    SEND,
                    () -> {
                      nanoTime.addAndGet(Duration.ofMillis(50).toNanos());
                      throw cause;
                    }))
        .isSameAs(cause);

    assertThat(totalDurationMillis(meterRegistry, SEND))
        .isEqualTo(50);
    assertThat(failureTotal(meterRegistry, SEND)).isEqualTo(1);
    assertThat(lastCallOutcomes.lastCallSucceeded(Dependency.AZURE_SERVICE_BUS)).contains(false);
  }

  @Test
  void elapsedSince_shouldBeTheTimeSinceCallStartedAt() {
    long startedAt = recorder.callStartedAt();
    nanoTime.addAndGet(Duration.ofMillis(12).toNanos());

    assertThat(recorder.elapsedSince(startedAt)).isEqualTo(Duration.ofMillis(12));
  }
}
