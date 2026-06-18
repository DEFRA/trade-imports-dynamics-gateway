package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSessionReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import java.time.Duration;
import java.util.Optional;
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

    @MockitoSpyBean
    private ServiceBusSenderClient senderClient;

    @AfterEach
    void resetSpy() {
        Mockito.reset(senderClient);
    }

    @Test
    void post_shouldReturnAccepted_andMessageLandsInQueue() {
        // Given
        String postedJson = "{\"eventType\":\"NotificationSubmitted\",\"aggregateId\":\"Imports.Notification.GBN-AG.GBN-AG-26-001\"}";

        // When
        ResponseEntity<Void> response = restTemplate.postForEntity(
            "/events", jsonEntity(postedJson), Void.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ServiceBusReceivedMessage received = receiveMessage();
        assertThat(received.getBody()).hasToString(postedJson);
        assertThat(received.getRawAmqpMessage().getProperties().getContentType()).isEqualTo("application/json");
        assertThat(received.getMessageId()).isNotBlank();
        assertThat(received.getSessionId()).isEqualTo("Imports.Notification.GBN-AG.GBN-AG-26-001");
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
        assertThat(receiveNoMessage()).isEmpty();
    }

    static Stream<Arguments> badRequestCases() {
        return Stream.of(
            Arguments.of("missing body", null, "bad-request"),
            Arguments.of("malformed JSON", "{bad json", null),
            Arguments.of("missing aggregateId", "{\"eventType\":\"NotificationSubmitted\"}", "aggregateId is required"),
            Arguments.of("blank aggregateId", "{\"aggregateId\":\"  \"}", "aggregateId is required")
        );
    }

    @Test
    void post_shouldReturnBadGateway_whenSendFails() {
        // Given
        doThrow(new RuntimeException("ASB send failed")).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/events", jsonEntity("{\"aggregateId\":\"Imports.Notification.GBN-AG.GBN-AG-26-001\"}"), String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).contains("error");
    }

    private ServiceBusReceivedMessage receiveMessage() {
        try (ServiceBusSessionReceiverClient sessionReceiver = new ServiceBusClientBuilder()
                .connectionString(SERVICE_BUS_CONTAINER.getConnectionString())
                .sessionReceiver()
                .queueName(QUEUE_NAME)
                .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE)
                .buildClient();
             ServiceBusReceiverClient receiver = sessionReceiver.acceptNextSession()) {
            return receiver.receiveMessages(1, Duration.ofSeconds(10))
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("No message received from queue within timeout"));
        }
    }

    private Optional<ServiceBusReceivedMessage> receiveNoMessage() {
        try (ServiceBusSessionReceiverClient sessionReceiver = new ServiceBusClientBuilder()
                .connectionString(SERVICE_BUS_CONTAINER.getConnectionString())
                .retryOptions(new AmqpRetryOptions().setTryTimeout(Duration.ofSeconds(3)).setMaxRetries(0))
                .sessionReceiver()
                .queueName(QUEUE_NAME)
                .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE)
                .buildClient();
             ServiceBusReceiverClient receiver = sessionReceiver.acceptNextSession()) {
            return receiver.receiveMessages(1, Duration.ofSeconds(1))
                .stream()
                .findFirst();
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
