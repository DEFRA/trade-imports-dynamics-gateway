package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import java.time.Duration;
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
    void post_returnsAccepted_andMessageLandsInQueue() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
            "/events", jsonEntity("{\"eventType\":\"NotificationSubmitted\"}"), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ServiceBusReceivedMessage received = receiveMessage();
        assertThat(received.getBody().toString()).contains("NotificationSubmitted");
        assertThat(received.getMessageId()).isNotBlank();
    }

    @Test
    void post_returnsBadRequest_forMissingBody() {
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/events", jsonEntity(null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("error");
    }

    @Test
    void post_returnsBadRequest_forMalformedJson() {
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/events", jsonEntity("{bad json"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void post_returnsBadGateway_whenSendFails() {
        doThrow(new RuntimeException("ASB send failed")).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/events", jsonEntity("{}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).contains("error");
    }

    private ServiceBusReceivedMessage receiveMessage() {
        try (ServiceBusReceiverClient receiver = new ServiceBusClientBuilder()
                .connectionString(SERVICE_BUS_CONTAINER.getConnectionString())
                .receiver()
                .queueName(QUEUE_NAME)
                .buildClient()) {
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
