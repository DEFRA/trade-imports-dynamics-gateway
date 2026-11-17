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
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import uk.gov.defra.cdp.dynamicsgateway.metrics.EmfMeterRegistry;
import uk.gov.defra.cdp.dynamicsgateway.testsupport.CapturedConsole;

@TestPropertySource(
    properties = {
      "aws.emf.enabled=true",
      "aws.emf.environment=Local",
      "aws.emf.namespace=trade-imports-dynamics-gateway",
      "aws.emf.step=1s"
    })
class EmfMeterRegistryIT extends IntegrationBase {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MeterRegistry meterRegistry;
  @Autowired private TestRestTemplate restTemplate;
  @MockitoSpyBean private ServiceBusSenderClient senderClient;

  @BeforeEach
  void sendNothingToTheServiceBus() {
    doNothing().when(senderClient).sendMessage(any(ServiceBusMessage.class));
  }

  @AfterEach
  void resetSpy() {
    Mockito.reset(senderClient);
  }

  @Test
  void theMeterRegistryInUseIsTheEmfMeterRegistry() {
    assertThat(meterRegistry).isInstanceOf(EmfMeterRegistry.class);
  }

  @Test
  void theMetersAPublishedEventRecordsAndEveryGatewayMeterAreLetThroughTheAllowlist() {
    publishEvent();

    for (String name :
        List.of(
            "external.call.duration",
            "external.call.failure",
            "external.dependency.available",
            "notification.message",
            "notification.error",
            "sqs.queue.depth",
            "controller.publishEvent.time")) {
      assertThat(meterRegistry.find(name).meter()).as(name).isNotNull();
    }
  }

  @Test
  void theRegistryPublishesARecordedSendOnItsOwnSchedule() throws Exception {
    CapturedConsole console = captureSystemOut();
    try (console) {
      publishEvent();

      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofSeconds(1))
          .until(() -> emfDocumentFor("external.call.duration", console).isPresent());
    }

    JsonNode document = emfDocumentFor("external.call.duration", console).orElseThrow();
    assertThat(namespaceOf(document)).isEqualTo("trade-imports-dynamics-gateway");
    assertThat(document.path("dependency").asText()).isEqualTo("azure-service-bus");
  }

  @ParameterizedTest
  @ValueSource(strings = {"http.server.requests", "notification.sqs.messages", "jvm.gc.pause"})
  void aMeterOffTheAllowlistIsNeverRegistered(String name) {
    meterRegistry.counter(name).increment();

    assertThat(meterRegistry.find(name).meter()).isNull();
  }

  private void publishEvent() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    restTemplate.postForEntity(
        "/events", new HttpEntity<>("{\"aggregateId\":\"emf-registry-session\"}", headers), Void.class);
  }

  private static Optional<JsonNode> emfDocumentFor(String metricName, CapturedConsole console)
      throws IOException {
    for (String line : console.text().split("\n")) {
      if (line.contains("\"_aws\"") && line.contains("\"" + metricName + "\"")) {
        return Optional.of(JSON.readTree(line));
      }
    }
    return Optional.empty();
  }
}
