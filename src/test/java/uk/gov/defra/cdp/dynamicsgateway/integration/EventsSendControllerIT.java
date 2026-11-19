package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.AZURE_SERVICE_BUS;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.SEND_MESSAGE;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.durationCount;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.failureTotal;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSessionReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class EventsSendControllerIT extends IntegrationBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoSpyBean
    private ServiceBusSenderClient senderClient;

    @AfterEach
    void resetSpy() {
        Mockito.reset(senderClient);
    }

    @Test
    void post_shouldReturnAccepted_messageLandsInQueue_andTheSendIsRecordedWithNoFailure() {
        // Given
        long sendsBefore = durationCount(meterRegistry, AZURE_SERVICE_BUS, SEND_MESSAGE);
        double failuresBefore = failureTotal(meterRegistry, AZURE_SERVICE_BUS, SEND_MESSAGE);
        String aggregateId = "Imports.Notification.GBN-AG.GBN-AG-26-" + UUID.randomUUID();
        String postedJson = "{\"eventType\":\"NotificationSubmitted\",\"aggregateId\":\"" + aggregateId + "\"}";

        // When
        ResponseEntity<Void> response = restTemplate.postForEntity(
            "/events", jsonEntity(postedJson), Void.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ServiceBusReceivedMessage received = receiveMessage(aggregateId);
        assertThat(received.getBody()).hasToString(postedJson);
        assertThat(received.getRawAmqpMessage().getProperties().getContentType()).isEqualTo("application/json");
        assertThat(received.getMessageId()).isNotBlank();
        assertThat(received.getSessionId()).isEqualTo(aggregateId);
        assertThat(durationCount(meterRegistry, AZURE_SERVICE_BUS, SEND_MESSAGE)).isEqualTo(sendsBefore + 1);
        assertThat(failureTotal(meterRegistry, AZURE_SERVICE_BUS, SEND_MESSAGE)).isEqualTo(failuresBefore);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badRequestCases")
    void post_shouldReturnBadRequest(String description, String body, String expectedBodyContains) {
        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/events", jsonEntity(body), String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        if (expectedBodyContains != null) {
            assertThat(response.getBody()).contains(expectedBodyContains);
        }
        verify(senderClient, never()).sendMessage(any(ServiceBusMessage.class));
    }

    static Stream<Arguments> badRequestCases() {
        return Stream.of(
            Arguments.of("missing body", null, "bad-request"),
            Arguments.of("malformed JSON", "{bad json", null),
            Arguments.of("missing aggregateId", "{\"eventType\":\"NotificationSubmitted\"}", "Request parameter is invalid"),
            Arguments.of("blank aggregateId", "{\"aggregateId\":\"  \"}", "Request parameter is invalid")
        );
    }

    @Test
    void post_shouldReturnBadGateway_andRecordAFailedSend_whenSendFails() {
        // Given
        double failuresBefore = failureTotal(meterRegistry, AZURE_SERVICE_BUS, SEND_MESSAGE);
        doThrow(new RuntimeException("ASB send failed")).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/events", jsonEntity("{\"aggregateId\":\"Imports.Notification.GBN-AG.GBN-AG-26-001\"}"), String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).contains("error");
        assertThat(failureTotal(meterRegistry, AZURE_SERVICE_BUS, SEND_MESSAGE)).isEqualTo(failuresBefore + 1);
    }

    private ServiceBusReceivedMessage receiveMessage(String sessionId) {
        try (ServiceBusSessionReceiverClient sessionReceiver = new ServiceBusClientBuilder()
                .connectionString(SERVICE_BUS_CONTAINER.getConnectionString())
                .sessionReceiver()
                .queueName(QUEUE_NAME)
                .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE)
                .buildClient();
             ServiceBusReceiverClient receiver = sessionReceiver.acceptSession(sessionId)) {
            return receiver.receiveMessages(1, Duration.ofSeconds(10))
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("No message received from queue within timeout"));
        }
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
