package uk.gov.defra.cdp.dynamicsgateway.common;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.DynamicsGatewayException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Rejected request — unreadable body: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "Request body is missing or not valid JSON"));
    }

    @ExceptionHandler(DynamicsGatewayException.class)
    public ResponseEntity<Map<String, String>> handleGatewayException(DynamicsGatewayException ex) {
        log.error("Gateway error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("error", "Failed to forward event to Azure Service Bus"));
    }
}
