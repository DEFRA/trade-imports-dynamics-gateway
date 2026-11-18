package uk.gov.defra.cdp.dynamicsgateway.testsupport;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import uk.gov.defra.cdp.dynamicsgateway.metrics.Dependency;
import uk.gov.defra.cdp.dynamicsgateway.metrics.ExternalCall;

public final class ExternalCallMeters {

  public static final String AZURE_SERVICE_BUS = Dependency.AZURE_SERVICE_BUS.tagValue();
  public static final String SEND_MESSAGE = ExternalCall.SERVICE_BUS_SEND_MESSAGE.operation();

  private ExternalCallMeters() {}

  public static long durationCount(MeterRegistry registry, String dependency, String operation) {
    return duration(registry, dependency, operation).map(Timer::count).orElse(0L);
  }

  public static double totalDurationMillis(
      MeterRegistry registry, String dependency, String operation) {
    return duration(registry, dependency, operation)
        .map(timer -> timer.totalTime(TimeUnit.MILLISECONDS))
        .orElse(0.0);
  }

  public static long recordedCallCount(
      MeterRegistry registry, String dependency, String operation) {
    return failure(registry, dependency, operation).map(DistributionSummary::count).orElse(0L);
  }

  public static double failureTotal(MeterRegistry registry, String dependency, String operation) {
    return failure(registry, dependency, operation)
        .map(DistributionSummary::totalAmount)
        .orElse(0.0);
  }

  public static double availability(MeterRegistry registry, String dependency) {
    return registry
        .get("external.dependency.available")
        .tag("dependency", dependency)
        .gauge()
        .value();
  }

  private static Optional<Timer> duration(
      MeterRegistry registry, String dependency, String operation) {
    return Optional.ofNullable(
        registry
            .find("external.call.duration")
            .tags("dependency", dependency, "operation", operation)
            .timer());
  }

  private static Optional<DistributionSummary> failure(
      MeterRegistry registry, String dependency, String operation) {
    return Optional.ofNullable(
        registry
            .find("external.call.failure")
            .tags("dependency", dependency, "operation", operation)
            .summary());
  }
}
