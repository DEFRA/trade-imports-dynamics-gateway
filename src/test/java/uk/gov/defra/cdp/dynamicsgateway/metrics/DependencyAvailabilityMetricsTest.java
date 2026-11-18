package uk.gov.defra.cdp.dynamicsgateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.availability;

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
  void availabilityGauge_shouldBeNaNForEachDependency_whenItHasNotBeenCalled() {
    assertThat(availability(meterRegistry, "azure-service-bus")).isNaN();
    assertThat(availability(meterRegistry, "sqs")).isNaN();
  }

  @Test
  void availabilityGauge_shouldBeOneAfterASuccessAndZeroAfterAFailure_independentlyPerDependency() {
    lastCallOutcomes.recordSuccess(Dependency.AZURE_SERVICE_BUS);
    lastCallOutcomes.recordFailure(Dependency.SQS);

    assertThat(availability(meterRegistry, "azure-service-bus")).isEqualTo(1.0);
    assertThat(availability(meterRegistry, "sqs")).isZero();

    lastCallOutcomes.recordFailure(Dependency.AZURE_SERVICE_BUS);
    lastCallOutcomes.recordSuccess(Dependency.SQS);

    assertThat(availability(meterRegistry, "azure-service-bus")).isZero();
    assertThat(availability(meterRegistry, "sqs")).isEqualTo(1.0);
  }
}
