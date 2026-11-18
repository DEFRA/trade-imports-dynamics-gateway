package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.CapturedConsole.captureSystemOut;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import uk.gov.defra.cdp.dynamicsgateway.testsupport.CapturedConsole;

class ExternalCallRecorderIT extends IntegrationBase {

  private static final String SEND_ACTION = "azure-service-bus/send-message";

  @MockitoSpyBean private ServiceBusSenderClient senderClient;

  @Test
  void theConsoleWritesTheSendLineWithTheTraceIdAndANestedEventObject() {
    doNothing().when(senderClient).sendMessage(any(ServiceBusMessage.class));

    CapturedConsole console = captureSystemOut();
    try (console) {
      postEvent("call-line-session", "trace-from-the-caller");
    }

    JsonNode send = sendLine(console);

    assertThat(send.path("trace.id").asText()).isEqualTo("trace-from-the-caller");
    assertThat(send.path("event").path("category").asText()).isEqualTo("external-call");
    assertThat(send.path("event").path("outcome").asText()).isEqualTo("success");
    assertThat(send.path("event").path("duration").isIntegralNumber()).isTrue();
  }

  @Test
  void theConsoleWritesAFailedSendLineWithTheErrorTypeAndMessageButNoStackTrace() {
    doThrow(new IllegalStateException("sender disposed"))
        .when(senderClient)
        .sendMessage(any(ServiceBusMessage.class));

    CapturedConsole console = captureSystemOut();
    try (console) {
      postEvent("call-line-session", "trace-of-a-failed-send");
    }

    JsonNode failedSend = sendLine(console);

    assertThat(failedSend.path("event").path("outcome").asText()).isEqualTo("failure");
    assertThat(failedSend.path("error.type").asText()).isEqualTo("java.lang.IllegalStateException");
    assertThat(failedSend.path("error.message").asText()).isEqualTo("sender disposed");
    assertThat(failedSend.has("error.stack_trace")).isFalse();
  }

  private static JsonNode sendLine(CapturedConsole console) {
    return console
        .firstJsonLine(line -> SEND_ACTION.equals(line.path("event").path("action").asText()))
        .orElseThrow(() -> new AssertionError("No console line has event.action " + SEND_ACTION));
  }
}
