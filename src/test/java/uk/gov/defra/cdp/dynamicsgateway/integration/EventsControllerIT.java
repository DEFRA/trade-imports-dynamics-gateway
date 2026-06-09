package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.DynamicsGatewayException;

class EventsControllerIT extends IntegrationBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private ServiceBusSenderClient senderClient;

    @Test
    void post_returnsAccepted_forValidJson() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
            "/events", jsonEntity("{\"eventType\":\"NotificationSubmitted\"}"), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(senderClient).sendMessage(any(ServiceBusMessage.class));
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
        doThrow(new DynamicsGatewayException("ASB error")).when(senderClient).sendMessage(any());

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/events", jsonEntity("{}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).contains("error");
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
