package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.CapturedConsole.captureSystemOut;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.EmfDocuments.namespaceOf;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import uk.gov.defra.cdp.dynamicsgateway.metrics.EmfMeterRegistry;
import uk.gov.defra.cdp.dynamicsgateway.testsupport.CapturedConsole;

@TestPropertySource(
    properties = {
      "aws.emf.enabled=true",
      "aws.emf.environment=Local",
      "aws.emf.namespace=trade-imports-dynamics-gateway",
      "aws.emf.step=1s",
      "management.endpoints.web.exposure.include=health,metrics"
    })
class EmfMeterRegistryIT extends IntegrationBase {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MeterRegistry meterRegistry;
  @Autowired private EmfMeterRegistry emfMeterRegistry;
  @Autowired private TestRestTemplate restTemplate;
  @MockitoSpyBean private ServiceBusSenderClient senderClient;

  @BeforeEach
  void sendNothingToTheServiceBus() {
    doNothing().when(senderClient).sendMessage(any(ServiceBusMessage.class));
  }

  @Test
  void thePrimaryRegistryIsACompositeOfTheEmfAndTheCumulativeRegistries() {
    assertThat(meterRegistry)
        .isInstanceOfSatisfying(
            CompositeMeterRegistry.class,
            composite ->
                assertThat(composite.getRegistries())
                    .hasSize(2)
                    .anyMatch(EmfMeterRegistry.class::isInstance)
                    .anyMatch(SimpleMeterRegistry.class::isInstance));
  }

  @Test
  void theMetricsEndpointReadsTheRunningTotalAfterTheEmfStepHasPassed() {
    meterRegistry
        .counter("notification.message", "outcome", "forwarded", "schema.version", "9.9.9")
        .increment(3);

    await()
        .pollDelay(Duration.ofMillis(2500))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        countOf(
                            "/metrics/notification.message"
                                + "?tag=outcome:forwarded&tag=schema.version:9.9.9"))
                    .isEqualTo(3.0));
  }

  @Test
  void aPublishedEventsMetersAndTheGatewayGaugesReachTheEmfRegistry() {
    postEvent("emf-registry-session");

    for (String name :
        List.of(
            "external.call.duration",
            "external.call.failure",
            "external.dependency.available",
            "notification.message",
            "notification.error",
            "sqs.queue.depth",
            "controller.publishEvent.time")) {
      assertThat(emfMeterRegistry.find(name).meter()).as(name).isNotNull();
    }
  }

  @Test
  void theRegistryPublishesARecordedSendOnItsOwnSchedule() {
    JsonNode document;
    CapturedConsole console = captureSystemOut();
    try (console) {
      postEvent("emf-registry-session");

      document =
          await()
              .atMost(Duration.ofSeconds(10))
              .pollInterval(Duration.ofSeconds(1))
              .until(() -> serviceBusSendDocument(console), Optional::isPresent)
              .orElseThrow();
    }

    assertThat(namespaceOf(document)).isEqualTo("trade-imports-dynamics-gateway");
    assertThat(document.path("dependency").asText()).isEqualTo("azure-service-bus");
  }

  @ParameterizedTest
  @ValueSource(strings = {"http.server.requests", "notification.sqs.messages", "jvm.gc.pause"})
  void aMeterOffTheAllowlistIsNeverRegistered(String name) {
    meterRegistry.counter(name).increment();

    assertThat(emfMeterRegistry.find(name).meter()).isNull();
  }

  private double countOf(String metricsPath) throws IOException {
    JsonNode body = JSON.readTree(restTemplate.getForObject(metricsPath, String.class));
    for (JsonNode measurement : body.path("measurements")) {
      if ("COUNT".equals(measurement.path("statistic").asText())) {
        return measurement.path("value").asDouble();
      }
    }
    throw new IllegalStateException("No COUNT measurement in " + body);
  }

  private static Optional<JsonNode> serviceBusSendDocument(CapturedConsole console) {
    return console.firstJsonLine(
        line ->
            line.has("_aws")
                && line.has("external.call.duration")
                && "azure-service-bus".equals(line.path("dependency").asText()));
  }
}
