package uk.gov.defra.cdp.dynamicsgateway.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.Map;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleUnreadableBody_shouldReturn400WithErrorMessage() {
        // Given
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        // When
        ResponseEntity<Map<String, String>> response = handler.handleUnreadableBody(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
        assertThat(response.getBody().get("error")).contains("not valid JSON");
    }

    @Test
    void handleNoResource_shouldReturn404() {
        // Given
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/actuator");

        // When
        ResponseEntity<Void> response = handler.handleNoResource(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleGatewayException_shouldReturn502WithErrorMessage() {
        // Given
        DynamicsGatewayException ex = new DynamicsGatewayException("ASB send failed");

        // When
        ResponseEntity<Map<String, String>> response = handler.handleGatewayException(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).containsKey("error");
        assertThat(response.getBody().get("error")).contains("Azure Service Bus");
    }
}
