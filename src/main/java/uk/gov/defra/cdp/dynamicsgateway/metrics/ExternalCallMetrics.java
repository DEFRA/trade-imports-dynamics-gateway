package uk.gov.defra.cdp.dynamicsgateway.metrics;

import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.cloudwatchlogs.emf.model.DimensionSet;
import software.amazon.cloudwatchlogs.emf.model.Unit;

/**
 * Measures each call to a system outside the INS boundary and emits one CloudWatch Embedded Metric
 * Format document per call.
 *
 * <p>Each document is published to the service's namespace with the single dimension set
 * {@code [Dependency, Operation]}. It carries {@code ExternalCallDuration} in milliseconds and
 * {@code ExternalCallFailure} as 1 for a failed call and 0 for a successful one, plus the
 * {@code Outcome} and, where the call has one, the {@code Interface} properties. Recording never
 * breaks a call: a failure to record is logged as a warning and swallowed.
 */
@Slf4j
@Component
public class ExternalCallMetrics {

  static final String DURATION_METRIC = "ExternalCallDuration";
  static final String FAILURE_METRIC = "ExternalCallFailure";
  private static final double NANOS_PER_MILLISECOND = 1_000_000.0;

  private final boolean enabled;
  private final String namespace;
  private final Supplier<MetricsLogger> metricsLoggers;

  @Autowired
  public ExternalCallMetrics(
      @Value("${aws.emf.enabled:true}") boolean enabled,
      @Value("${aws.emf.namespace}") String namespace) {
    this(enabled, namespace, MetricsLogger::new);
  }

  /**
   * Supplies each call's {@code MetricsLogger}, so a test can capture the EMF output.
   *
   * @param enabled whether to record at all
   * @param namespace the CloudWatch namespace
   * @param metricsLoggers supplies a fresh logger for each recorded call
   */
  public ExternalCallMetrics(
      boolean enabled, String namespace, Supplier<MetricsLogger> metricsLoggers) {
    this.enabled = enabled;
    this.namespace = namespace;
    this.metricsLoggers = metricsLoggers;
  }

  /**
   * Runs the action, records its duration and outcome, and rethrows anything it throws unchanged.
   *
   * @param call the call being measured
   * @param action the call to run
   */
  public void measure(ExternalCall call, Runnable action) {
    long startedAt = System.nanoTime();
    try {
      action.run();
    } catch (RuntimeException e) {
      record(call, Duration.ofNanos(System.nanoTime() - startedAt), true);
      throw e;
    }
    record(call, Duration.ofNanos(System.nanoTime() - startedAt), false);
  }

  /**
   * Emits one document for a finished call. Never throws.
   *
   * @param call the call that finished
   * @param elapsed how long it took
   * @param failed whether it failed
   */
  public void record(ExternalCall call, Duration elapsed, boolean failed) {
    if (!enabled) {
      return;
    }
    try {
      var metricsLogger = metricsLoggers.get();
      metricsLogger.setNamespace(namespace);
      metricsLogger.setDimensions(
          DimensionSet.of("Dependency", call.dependency(), "Operation", call.operation()));
      metricsLogger.putMetric(
          DURATION_METRIC, elapsed.toNanos() / NANOS_PER_MILLISECOND, Unit.MILLISECONDS);
      metricsLogger.putMetric(FAILURE_METRIC, failed ? 1 : 0, Unit.COUNT);
      metricsLogger.putProperty("Outcome", failed ? "failure" : "success");
      if (call.interfaceId() != null) {
        metricsLogger.putProperty("Interface", call.interfaceId());
      }
      metricsLogger.flush();
    } catch (RuntimeException e) {
      log.warn(
          "Could not record external call {}/{}: {}",
          call.dependency(),
          call.operation(),
          e.getMessage());
    }
  }
}
