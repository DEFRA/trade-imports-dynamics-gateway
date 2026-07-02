package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private static final String EVENT_ID = "evt-99999999-8888-7777-6666-555555555555";
    private static final String RECEIVE_COUNT = "1";
    private static final String VALID_BODY =
        "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";
    private static final String ENVELOPED_BODY =
        "{\"eventId\":\"" + EVENT_ID + "\",\"aggregateId\":\"" + AGGREGATE_ID
            + "\",\"eventType\":\"NotificationSubmitted\"}";

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
        listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        verify(queueMessageSender).publish(VALID_BODY, AGGREGATE_ID, DEDUP_ID);
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldForwardWithGeneratedUuid_whenDedupHeaderAbsentAndNoEventId() {
        // Since the messageId is now resolved once in the listener (not left to publish's own
        // null-fallback), a missing header no longer passes null through — it's a freshly minted UUID.
        listener.receive(VALID_BODY, AGGREGATE_ID, null, RECEIVE_COUNT);

        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(queueMessageSender).publish(eq(VALID_BODY), eq(AGGREGATE_ID), messageIdCaptor.capture());
        assertThatCode(() -> UUID.fromString(messageIdCaptor.getValue())).doesNotThrowAnyException();
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldUseBodyEventIdAsAsbMessageId_whenPresent_evenIfDedupHeaderDiffers() {
        // The SQS dedup header can legitimately differ from the body eventId on a DLQ replay (DlqService
        // mints a fresh transport dedup id per replay) — the ASB messageId must still track the body
        // eventId, not the header, so the ASB-level dedup key stays stable across the replay.
        listener.receive(ENVELOPED_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        verify(queueMessageSender).publish(ENVELOPED_BODY, AGGREGATE_ID, EVENT_ID);
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldUseSameGeneratedId_acrossRetries_whenNoEventIdAndNoDedupHeader() {
        // Given — the regression this fix targets: previously each in-process retry attempt minted its
        // own fallback UUID inside QueueMessageSender.publish, so a "transient" failure that was actually
        // a false negative (ASB durably received the message; only the ack was lost) could duplicate-
        // publish under a different id per attempt, defeating ASB duplicate detection if ever enabled.
        doThrow(new SqsRetryableException("ASB blip", new RuntimeException("transient")))
            .doNothing()
            .when(queueMessageSender).publish(any(), any(), any());

        // When
        listener.receive(VALID_BODY, AGGREGATE_ID, null, RECEIVE_COUNT);

        // Then — both attempts (the failed one and the recovering one) used the identical resolved id.
        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(queueMessageSender, times(2))
            .publish(eq(VALID_BODY), eq(AGGREGATE_ID), messageIdCaptor.capture());
        List<String> messageIds = messageIdCaptor.getAllValues();
        assertThat(messageIds.get(1)).isEqualTo(messageIds.get(0));
        assertThatCode(() -> UUID.fromString(messageIds.get(0))).doesNotThrowAnyException();
    }

    @Test
    void receive_shouldTreatBlankDedupHeaderAsAbsent_andMintOneStableIdAcrossRetries() {
        // Given — a blank (non-null) header must not be treated as "present" and skip UUID generation.
        doThrow(new SqsRetryableException("ASB blip", new RuntimeException("transient")))
            .doNothing()
            .when(queueMessageSender).publish(any(), any(), any());

        // When
        listener.receive(VALID_BODY, AGGREGATE_ID, "   ", RECEIVE_COUNT);

        // Then
        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(queueMessageSender, times(2))
            .publish(eq(VALID_BODY), eq(AGGREGATE_ID), messageIdCaptor.capture());
        List<String> messageIds = messageIdCaptor.getAllValues();
        assertThat(messageIds.get(1)).isEqualTo(messageIds.get(0));
        assertThatCode(() -> UUID.fromString(messageIds.get(0))).doesNotThrowAnyException();
    }

    @Test
    void receive_shouldUseSameBodyEventId_acrossRetries() {
        // Given — regression coverage for the eventId path (already stable via the finding-1 fix; this
        // locks it in explicitly rather than relying only on the single-attempt assertion elsewhere).
        doThrow(new SqsRetryableException("ASB blip", new RuntimeException("transient")))
            .doNothing()
            .when(queueMessageSender).publish(any(), any(), any());

        // When
        listener.receive(ENVELOPED_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        // Then — every attempt uses the body eventId, not the (differing) SQS dedup header.
        verify(queueMessageSender, times(2)).publish(ENVELOPED_BODY, AGGREGATE_ID, EVENT_ID);
    }

    @Test
    void receive_shouldForward_whenReceiveCountHeaderAbsent() {
        listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, null);

        verify(queueMessageSender).publish(VALID_BODY, AGGREGATE_ID, DEDUP_ID);
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldThrowNonRetryable_whenAggregateIdIsNull() {
        assertThatThrownBy(() -> listener.receive(VALID_BODY, null, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("MESSAGE_GROUP_ID");

        verify(queueMessageSender, never()).publish(any(), any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenAggregateIdIsBlank() {
        assertThatThrownBy(() -> listener.receive(VALID_BODY, "  ", DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("MESSAGE_GROUP_ID");

        verify(queueMessageSender, never()).publish(any(), any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenBodyIsNull() {
        assertThatThrownBy(() -> listener.receive(null, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessage("Empty message body");

        verify(queueMessageSender, never()).publish(any(), any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenBodyIsBlank() {
        assertThatThrownBy(() -> listener.receive("  ", AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessage("Empty message body");

        verify(queueMessageSender, never()).publish(any(), any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenBodyIsInvalidJson() {
        assertThatThrownBy(() -> listener.receive("not-json", AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("not valid JSON");

        verify(queueMessageSender, never()).publish(any(), any(), any());
    }

    @Test
    void receive_shouldRetryThenThrowRetryable_whenAsbFailsTransiently() {
        // Given
        doThrow(new SqsRetryableException("ASB down", new RuntimeException("Simulated transient failure")))
            .when(queueMessageSender).publish(any(), any(), any());

        // When / Then — retried in-process up to maxAttempts before propagating to the SQS error handler.
        assertThatThrownBy(() -> listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsRetryableException.class);
        verify(queueMessageSender, times(MAX_ATTEMPTS)).publish(VALID_BODY, AGGREGATE_ID, DEDUP_ID);
        assertThat(counterValue("forwarded")).isEqualTo(0.0);
    }

    @Test
    void receive_shouldForwardOnRetry_whenTransientFailureRecoversWithinWindow() {
        // Given
        doThrow(new SqsRetryableException("ASB blip", new RuntimeException("transient")))
            .doNothing()
            .when(queueMessageSender).publish(any(), any(), any());

        // When
        listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        // Then
        verify(queueMessageSender, times(2)).publish(VALID_BODY, AGGREGATE_ID, DEDUP_ID);
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldNotRetryAndThrowNonRetryable_whenAsbFailsPermanently() {
        // Given
        doThrow(new SqsNonRetryableException("entity not found", new RuntimeException("Simulated permanent failure")))
            .when(queueMessageSender).publish(any(), any(), any());

        // When / Then — non-retryable failures are not retried: single attempt then propagate to discard.
        assertThatThrownBy(() -> listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsNonRetryableException.class);
        verify(queueMessageSender, times(1)).publish(VALID_BODY, AGGREGATE_ID, DEDUP_ID);
        assertThat(counterValue("forwarded")).isEqualTo(0.0);
    }

    private double counterValue(String outcome) {
        return meterRegistry.counter("notification.sqs.messages", "outcome", outcome).count();
    }
}
