package uk.gov.defra.cdp.dynamicsgateway.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Registers the gauge {@code external.dependency.available}, tagged {@code dependency}, for each
 * dependency: 1 after its most recent call succeeded, 0 after it failed, and NaN until it has been
 * called.
 */
@Component
public class DependencyAvailabilityMetrics {

  DependencyAvailabilityMetrics(MeterRegistry meterRegistry, LastCallOutcomes lastCallOutcomes) {
    for (String dependency : ExternalCall.DEPENDENCIES) {
      Gauge.builder(
              "external.dependency.available",
              lastCallOutcomes,
              outcomes -> gaugeValueOf(outcomes, dependency))
          .tag(ExternalCallRecorder.DEPENDENCY_TAG, dependency)
          .register(meterRegistry);
    }
  }

  private static double gaugeValueOf(LastCallOutcomes outcomes, String dependency) {
    return outcomes
        .lastCallSucceeded(dependency)
        .map(succeeded -> succeeded ? 1.0 : 0.0)
        .orElse(Double.NaN);
  }
}
