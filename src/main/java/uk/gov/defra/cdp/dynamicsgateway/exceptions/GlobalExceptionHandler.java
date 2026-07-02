package uk.gov.defra.cdp.dynamicsgateway.exceptions;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String MDC_TRACE_ID = "trace.id";
    private static final String BAD_REQUEST_TITLE = "Bad Request";
    private static final String BAD_REQUEST_TYPE = "/problems/bad-request";

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Rejected request — unreadable body: {}", ex.getMessage());
        return problemResponse(
            HttpStatus.BAD_REQUEST,
            BAD_REQUEST_TITLE,
            BAD_REQUEST_TYPE,
            "Request body is missing or not valid JSON"
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Rejected request — invalid argument: {}", ex.getMessage());
        return problemResponse(
            HttpStatus.BAD_REQUEST,
            BAD_REQUEST_TITLE,
            BAD_REQUEST_TYPE,
            "Request parameter is invalid"
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Rejected request — constraint violation: {}", ex.getMessage());
        return problemResponse(
            HttpStatus.BAD_REQUEST,
            BAD_REQUEST_TITLE,
            BAD_REQUEST_TYPE,
            "Request parameter is invalid"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleInvalidBody(MethodArgumentNotValidException ex) {
        log.warn("Rejected request — invalid body: {}", ex.getMessage());
        return problemResponse(
            HttpStatus.BAD_REQUEST,
            BAD_REQUEST_TITLE,
            BAD_REQUEST_TYPE,
            "Request body failed validation"
        );
    }

    @ExceptionHandler(SqsRetryableException.class)
    public ResponseEntity<ProblemDetail> handleSqsRetryableException(SqsRetryableException ex) {
        log.error("Retryable upstream error: {}", ex.getMessage(), ex);
        return problemResponse(
            HttpStatus.BAD_GATEWAY,
            "Upstream Service Error",
            "/problems/upstream-error",
            "Failed to forward event to Azure Service Bus"
        );
    }

    @ExceptionHandler(SqsNonRetryableException.class)
    public ResponseEntity<ProblemDetail> handleSqsNonRetryableException(SqsNonRetryableException ex) {
        log.error("Non-retryable upstream error: {}", ex.getMessage(), ex);
        return problemResponse(
            HttpStatus.BAD_GATEWAY,
            "Upstream Service Error",
            "/problems/upstream-error",
            "Failed to forward event to Azure Service Bus"
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getMessage());
        return problemResponse(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported Media Type",
            "/problems/unsupported-media-type",
            "Content type is not supported"
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return problemResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            "/problems/internal-error",
            "An unexpected error occurred"
        );
    }

    private ResponseEntity<ProblemDetail> problemResponse(
        HttpStatus status,
        String title,
        String typePath,
        String detail
    ) {
        String traceId = MDC.get(MDC_TRACE_ID);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(typePath));
        problem.setTitle(title);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }
}
