package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.GenericMessage;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsRetryableException;

class NotificationErrorHandlerTest {

    private NotificationErrorHandler handler;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        handler = new NotificationErrorHandler(meterRegistry);
    }

    @Test
    void handle_shouldRethrow_whenSqsRetryableException() {
        SqsRetryableException retryable = new SqsRetryableException("transient", new RuntimeException("timeout"));

        assertThatThrownBy(() -> handler.handle(message(), retryable))
            .isInstanceOf(SqsRetryableException.class);
        assertThat(errorCount("retry")).isEqualTo(1.0);
    }

    @Test
    void handle_shouldReturn_whenSqsNonRetryableException() {
        SqsNonRetryableException nonRetryable = new SqsNonRetryableException("permanent", new RuntimeException("too large"));

        assertThatCode(() -> handler.handle(message(), nonRetryable))
            .doesNotThrowAnyException();
        assertThat(errorCount("discarded")).isEqualTo(1.0);
    }

    @Test
    void handle_shouldReturn_whenUnexpectedException() {
        RuntimeException unexpected = new RuntimeException("something unexpected");

        assertThatCode(() -> handler.handle(message(), unexpected))
            .doesNotThrowAnyException();
        assertThat(errorCount("discarded")).isEqualTo(1.0);
    }

    private GenericMessage<Object> message() {
        return new GenericMessage<>("test-body");
    }

    private double errorCount(String action) {
        return meterRegistry.counter("notification.sqs.errors", "action", action).count();
    }
}
