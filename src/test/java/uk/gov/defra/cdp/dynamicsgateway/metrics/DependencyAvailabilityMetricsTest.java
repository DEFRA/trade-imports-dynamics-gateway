package uk.gov.defra.cdp.dynamicsgateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class DependencyAvailabilityMetricsTest {

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final LastCallOutcomes lastCallOutcomes = new LastCallOutcomes();

  DependencyAvailabilityMetricsTest() {
    new DependencyAvailabilityMetrics(meterRegistry, lastCallOutcomes);
  }

  @Test
  void eachDependencyIsUnknownUntilItIsCalled() {
    assertThat(availability("azure-service-bus")).isNaN();
    assertThat(availability("sqs")).isNaN();
  }

  @Test
  void aDependencyIsAvailableAfterASuccessAndUnavailableAfterAFailure_independentlyOfTheOther() {
    lastCallOutcomes.recordSuccess("azure-service-bus");
    lastCallOutcomes.recordFailure("sqs");

    assertThat(availability("azure-service-bus")).isEqualTo(1.0);
    assertThat(availability("sqs")).isZero();

    lastCallOutcomes.recordFailure("azure-service-bus");
    lastCallOutcomes.recordSuccess("sqs");

    assertThat(availability("azure-service-bus")).isZero();
    assertThat(availability("sqs")).isEqualTo(1.0);
  }

  private double availability(String dependency) {
    return meterRegistry
        .get("external.dependency.available")
        .tag("dependency", dependency)
        .gauge()
        .value();
  }
}
