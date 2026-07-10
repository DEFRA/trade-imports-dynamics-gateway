package uk.gov.defra.cdp.dynamicsgateway.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParseException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sqs.model.PurgeQueueInProgressException;
import software.amazon.awssdk.services.sqs.model.SqsException;

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
            new JsonParseException(null, "Unexpected character"),
            (HttpInputMessage) null
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
            new JsonParseException(null, "Unexpected character"),
            (HttpInputMessage) null
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
    void handleSqsException_shouldReturn502_whenMoveTaskLimitExceeded() {
        // Given
        SqsException ex = (SqsException) SqsException.builder()
            .message("A message move task is already in progress")
            .awsErrorDetails(AwsErrorDetails.builder()
                .errorCode("AWS.SimpleQueueService.MessageMoveTask.LimitExceeded")
                .build())
            .build();

        // When
        ResponseEntity<ProblemDetail> response = handler.handleSqsException(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(problem).isNotNull();
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/upstream-error"));
    }

    @Test
    void handleSqsException_shouldReturn502_whenPurgeAlreadyInProgress() {
        // Given
        PurgeQueueInProgressException ex = PurgeQueueInProgressException.builder()
            .message("Purge already in progress")
            .build();

        // When
        ResponseEntity<ProblemDetail> response = handler.handleSqsException(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void handleSqsException_shouldReturn502_forOtherSqsErrors() {
        // Given
        SqsException ex = (SqsException) SqsException.builder()
            .message("Queue does not exist")
            .awsErrorDetails(AwsErrorDetails.builder().errorCode("AWS.SimpleQueueService.NonExistentQueue").build())
            .build();

        // When
        ResponseEntity<ProblemDetail> response = handler.handleSqsException(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(problem).isNotNull();
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/upstream-error"));
    }

    @Test
    void handleCompletionException_shouldUnwrapAndDelegate_whenCauseIsSqsException() {
        // Given — DlqService's .join() calls wrap whatever the SDK threw in a CompletionException
        SqsException cause = (SqsException) SqsException.builder()
            .message("A message move task is already in progress")
            .awsErrorDetails(AwsErrorDetails.builder()
                .errorCode("AWS.SimpleQueueService.MessageMoveTask.LimitExceeded")
                .build())
            .build();
        CompletionException ex = new CompletionException(cause);

        // When
        ResponseEntity<ProblemDetail> response = handler.handleCompletionException(ex);
        ProblemDetail problem = response.getBody();

        // Then — same mapping as calling handleSqsException(cause) directly
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(problem).isNotNull();
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/upstream-error"));
    }

    @Test
    void handleCompletionException_shouldReturn500_whenCauseIsNotSqsException() {
        // Given
        CompletionException ex = new CompletionException(new RuntimeException("boom"));

        // When
        ResponseEntity<ProblemDetail> response = handler.handleCompletionException(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(problem).isNotNull();
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/internal-error"));
    }

    @Test
    void handleIllegalArgument_shouldReturn400WithProblemDetail() {
        // Given
        MDC.put("trace.id", "trace-xyz");
        IllegalArgumentException ex = new IllegalArgumentException("aggregateId is required");

        // When
        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgument(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/bad-request"));
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        // The handler must not echo the raw exception message back to the caller (it may leak internals).
        assertThat(problem.getDetail()).isEqualTo("Request parameter is invalid");
        assertThat(problem.getProperties()).containsEntry("traceId", "trace-xyz");
    }

    @Test
    void handleConstraintViolation_shouldReturn400WithProblemDetail() {
        // Given
        ConstraintViolationException ex =
            new ConstraintViolationException("limit: must be less than or equal to 100", Set.of());

        // When
        ResponseEntity<ProblemDetail> response = handler.handleConstraintViolation(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(problem).isNotNull();
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/bad-request"));
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        // The handler must not echo the raw exception message back to the caller (it may leak internals).
        assertThat(problem.getDetail()).isEqualTo("Request parameter is invalid");
    }

    @Test
    void handleInvalidBody_shouldReturn400WithProblemDetail() {
        // Given
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getMessage()).thenReturn("field error: aggregateId must not be blank");

        // When
        ResponseEntity<ProblemDetail> response = handler.handleInvalidBody(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(problem).isNotNull();
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/bad-request"));
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail()).isEqualTo("Request body failed validation");
    }

    @Test
    void handleSqsRetryableException_shouldReturn502WithProblemDetail() {
        // Given
        SqsRetryableException ex = new SqsRetryableException("ASB timeout", new RuntimeException());

        // When
        ResponseEntity<ProblemDetail> response = handler.handleSqsRetryableException(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/upstream-error"));
        assertThat(problem.getDetail()).contains("Azure Service Bus");
    }

    @Test
    void handleSqsNonRetryableException_shouldReturn502WithProblemDetail() {
        // Given
        SqsNonRetryableException ex = new SqsNonRetryableException("message too large", new RuntimeException());

        // When
        ResponseEntity<ProblemDetail> response = handler.handleSqsNonRetryableException(ex);
        ProblemDetail problem = response.getBody();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/upstream-error"));
        assertThat(problem.getDetail()).contains("Azure Service Bus");
    }
}
