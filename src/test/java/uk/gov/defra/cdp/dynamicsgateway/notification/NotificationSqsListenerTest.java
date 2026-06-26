package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.support.RetryTemplate;
import uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsRetryableException;

@ExtendWith(MockitoExtension.class)
class NotificationSqsListenerTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private QueueMessageSender queueMessageSender;

    private NotificationSqsListener listener;
    private MeterRegistry meterRegistry;

    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    private static final String DEDUP_ID = "11111111-2222-3333-4444-555555555555";
    private static final String VALID_BODY =
        "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // Tiny backoff so retries run instantly; retries only SqsRetryableException, matching prod wiring.
        RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(MAX_ATTEMPTS)
            .fixedBackoff(1)
            .retryOn(SqsRetryableException.class)
            .build();
        listener = new NotificationSqsListener(
            queueMessageSender, new ObjectMapper(), retryTemplate, meterRegistry);
    }

    @Test
    void receive_shouldForwardToAsb_whenValid() {
        listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID);

        verify(queueMessageSender).publish(VALID_BODY, AGGREGATE_ID, DEDUP_ID);
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldForwardWithNullDedupId_whenHeaderAbsent() {
        listener.receive(VALID_BODY, AGGREGATE_ID, null);

        verify(queueMessageSender).publish(eq(VALID_BODY), eq(AGGREGATE_ID), isNull());
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldThrowNonRetryable_whenAggregateIdIsNull() {
        assertThatThrownBy(() -> listener.receive(VALID_BODY, null, DEDUP_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("MESSAGE_GROUP_ID");

        verify(queueMessageSender, never()).publish(any(), any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenAggregateIdIsBlank() {
        assertThatThrownBy(() -> listener.receive(VALID_BODY, "  ", DEDUP_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("MESSAGE_GROUP_ID");

        verify(queueMessageSender, never()).publish(any(), any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenBodyIsNull() {
        assertThatThrownBy(() -> listener.receive(null, AGGREGATE_ID, DEDUP_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessage("Empty message body");

        verify(queueMessageSender, never()).publish(any(), any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenBodyIsBlank() {
        assertThatThrownBy(() -> listener.receive("  ", AGGREGATE_ID, DEDUP_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessage("Empty message body");

        verify(queueMessageSender, never()).publish(any(), any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenBodyIsInvalidJson() {
        assertThatThrownBy(() -> listener.receive("not-json", AGGREGATE_ID, DEDUP_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("not valid JSON");

        verify(queueMessageSender, never()).publish(any(), any(), any());
    }

    @Test
    void receive_shouldRetryThenThrowRetryable_whenAsbFailsTransiently() {
        doThrow(new SqsRetryableException("ASB down", new RuntimeException("Simulated transient failure")))
            .when(queueMessageSender).publish(any(), any(), any());

        assertThatThrownBy(() -> listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID))
            .isInstanceOf(SqsRetryableException.class);
        // Retried in-process up to maxAttempts before propagating to the SQS error handler.
        verify(queueMessageSender, times(MAX_ATTEMPTS)).publish(VALID_BODY, AGGREGATE_ID, DEDUP_ID);
        assertThat(counterValue("forwarded")).isEqualTo(0.0);
    }

    @Test
    void receive_shouldForwardOnRetry_whenTransientFailureRecoversWithinWindow() {
        doThrow(new SqsRetryableException("ASB blip", new RuntimeException("transient")))
            .doNothing()
            .when(queueMessageSender).publish(any(), any(), any());

        listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID);

        verify(queueMessageSender, times(2)).publish(VALID_BODY, AGGREGATE_ID, DEDUP_ID);
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldNotRetryAndThrowNonRetryable_whenAsbFailsPermanently() {
        doThrow(new SqsNonRetryableException("entity not found", new RuntimeException("Simulated permanent failure")))
            .when(queueMessageSender).publish(any(), any(), any());

        assertThatThrownBy(() -> listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID))
            .isInstanceOf(SqsNonRetryableException.class);
        // Non-retryable failures are not retried — single attempt then propagate to discard.
        verify(queueMessageSender, times(1)).publish(VALID_BODY, AGGREGATE_ID, DEDUP_ID);
        assertThat(counterValue("forwarded")).isEqualTo(0.0);
    }

    private double counterValue(String outcome) {
        return meterRegistry.counter("notification.sqs.messages", "outcome", outcome).count();
    }
}
