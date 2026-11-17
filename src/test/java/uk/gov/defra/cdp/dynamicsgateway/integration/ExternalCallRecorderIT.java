package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.CapturedConsole.captureSystemOut;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import uk.gov.defra.cdp.dynamicsgateway.testsupport.CapturedConsole;

class ExternalCallRecorderIT extends IntegrationBase {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String SEND_ACTION = "azure-service-bus/send-message";

  @Autowired private TestRestTemplate restTemplate;
  @MockitoSpyBean private ServiceBusSenderClient senderClient;

  @AfterEach
  void resetSpy() {
    Mockito.reset(senderClient);
  }

  @Test
  void theConsoleWritesTheSendLineWithTheTraceIdAndANestedEventObject() throws Exception {
    doNothing().when(senderClient).sendMessage(any(ServiceBusMessage.class));

    CapturedConsole console = captureSystemOut();
    try (console) {
      publishEvent("trace-from-the-caller");
    }

    JsonNode send = consoleLineWithAction(SEND_ACTION, console);

    assertThat(send.path("trace.id").asText()).isEqualTo("trace-from-the-caller");
    assertThat(send.path("event").path("category").asText()).isEqualTo("external-call");
    assertThat(send.path("event").path("outcome").asText()).isEqualTo("success");
    assertThat(send.path("event").path("duration").isIntegralNumber()).isTrue();
  }

  @Test
  void theConsoleWritesAFailedSendLineWithTheErrorTypeAndMessageButNoStackTrace()
      throws Exception {
    doThrow(new IllegalStateException("sender disposed"))
        .when(senderClient)
        .sendMessage(any(ServiceBusMessage.class));

    CapturedConsole console = captureSystemOut();
    try (console) {
      publishEvent("trace-of-a-failed-send");
    }

    JsonNode failedSend = consoleLineWithAction(SEND_ACTION, console);

    assertThat(failedSend.path("event").path("outcome").asText()).isEqualTo("failure");
    assertThat(failedSend.path("error.type").asText()).isEqualTo("java.lang.IllegalStateException");
    assertThat(failedSend.path("error.message").asText()).isEqualTo("sender disposed");
    assertThat(failedSend.has("error.stack_trace")).isFalse();
  }

  private void publishEvent(String traceId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("x-cdp-request-id", traceId);
    restTemplate.postForEntity(
        "/events", new HttpEntity<>("{\"aggregateId\":\"call-line-session\"}", headers), String.class);
  }

  private static JsonNode consoleLineWithAction(String action, CapturedConsole console)
      throws Exception {
    for (String line : console.text().split("\n")) {
      if (line.trim().startsWith("{")) {
        JsonNode json = JSON.readTree(line);
        if (action.equals(json.path("event").path("action").asText())) {
          return json;
        }
      }
    }
    throw new AssertionError("No console line has event.action " + action);
  }
}
