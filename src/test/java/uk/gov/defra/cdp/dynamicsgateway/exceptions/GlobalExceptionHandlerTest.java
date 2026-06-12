package uk.gov.defra.cdp.dynamicsgateway.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParseException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void handleUnreadableBody_shouldReturn400WithProblemDetail() {
        // Given
        MDC.put("trace.id", "trace-abc");
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
            "Invalid JSON",
            new JsonParseException(null, "Unexpected character")
        );

        // When
        ResponseEntity<ProblemDetail> response = handler.handleUnreadableBody(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/bad-request"));
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail()).contains("not valid JSON");
        assertThat(problem.getProperties()).containsEntry("traceId", "trace-abc");
    }

    @Test
    void handleUnreadableBody_shouldOmitTraceIdWhenNotInMdc() {
        // Given
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
            "Invalid JSON",
            new JsonParseException(null, "Unexpected character")
        );

        // When
        ResponseEntity<ProblemDetail> response = handler.handleUnreadableBody(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(problem).isNotNull();
        if (problem.getProperties() != null) {
            assertThat(problem.getProperties()).doesNotContainKey("traceId");
        }
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
    void handleUnsupportedMediaType_shouldReturn415WithProblemDetail() {
        // Given
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
            MediaType.TEXT_PLAIN,
            List.of(MediaType.APPLICATION_JSON)
        );

        // When
        ResponseEntity<ProblemDetail> response = handler.handleUnsupportedMediaType(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/unsupported-media-type"));
        assertThat(problem.getDetail()).contains("not supported");
    }

    @Test
    void handleException_shouldReturn500WithProblemDetail() {
        // Given
        Exception ex = new RuntimeException("something went wrong");

        // When
        ResponseEntity<ProblemDetail> response = handler.handleException(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/internal-error"));
        assertThat(problem.getDetail()).contains("unexpected error");
    }

    @Test
    void handleGatewayException_shouldReturn502WithProblemDetail() {
        // Given
        DynamicsGatewayException ex = new DynamicsGatewayException("ASB send failed");

        // When
        ResponseEntity<ProblemDetail> response = handler.handleGatewayException(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/upstream-error"));
        assertThat(problem.getDetail()).contains("Azure Service Bus");
    }
}
