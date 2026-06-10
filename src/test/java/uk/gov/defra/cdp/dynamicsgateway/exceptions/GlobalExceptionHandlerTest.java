package uk.gov.defra.cdp.dynamicsgateway.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.DynamicsGatewayException;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.GlobalExceptionHandler;

import java.util.Map;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleUnreadableBody_returns400WithErrorMessage() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        ResponseEntity<Map<String, String>> response = handler.handleUnreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
        assertThat(response.getBody().get("error")).contains("not valid JSON");
    }

    @Test
    void handleGatewayException_returns502WithErrorMessage() {
        DynamicsGatewayException ex = new DynamicsGatewayException("ASB send failed");

        ResponseEntity<Map<String, String>> response = handler.handleGatewayException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).containsKey("error");
        assertThat(response.getBody().get("error")).contains("Azure Service Bus");
    }
}
