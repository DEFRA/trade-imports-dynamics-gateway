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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

    @Test
    void post_shouldReturnBadRequest_forMissingBody() {
        // Given — no body

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/events", jsonEntity(null), String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("bad-request");
        assertThat(receiveNoMessage()).isEmpty();
    }

    @Test
    void post_shouldReturnBadRequest_forMalformedJson() {
        // Given — malformed JSON body

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/events", jsonEntity("{bad json"), String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(receiveNoMessage()).isEmpty();
    }

    @Test
    void post_shouldReturnBadRequest_whenAggregateIdIsMissing() {
        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/events", jsonEntity("{\"eventType\":\"NotificationSubmitted\"}"), String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("aggregateId is required");
        assertThat(receiveNoMessage()).isEmpty();
    }

    @Test
    void post_shouldReturnBadRequest_whenAggregateIdIsBlank() {
        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/events", jsonEntity("{\"aggregateId\":\"  \"}"), String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("aggregateId is required");
        assertThat(receiveNoMessage()).isEmpty();
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
