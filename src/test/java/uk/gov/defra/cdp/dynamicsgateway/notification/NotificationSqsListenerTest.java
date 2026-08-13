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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsRetryableException;

@ExtendWith(MockitoExtension.class)
class NotificationSqsListenerTest {

    @Mock
    private QueueMessageSender queueMessageSender;

    private NotificationSqsListener listener;
    private MeterRegistry meterRegistry;

    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    private static final String DEDUP_ID = "11111111-2222-3333-4444-555555555555";
    private static final String EVENT_ID = "evt-99999999-8888-7777-6666-555555555555";
    private static final String RECEIVE_COUNT = "1";
    private static final String VALID_BODY =
        "{\"aggregateId\":\"" + AGGREGATE_ID
            + "\",\"eventType\":\"uk.gov.defra.imports.notification.NotificationSubmitted\"}";
    private static final String ENVELOPED_BODY =
        "{\"eventId\":\"" + EVENT_ID + "\",\"aggregateId\":\"" + AGGREGATE_ID
            + "\",\"eventType\":\"uk.gov.defra.imports.notification.NotificationSubmitted\"}";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PimsEventMapper pimsEventMapper = new PimsEventMapper(
            new PimsGbnAgDataMapper(
                new PimsConsignmentMapper(
                    new PimsTransportMapper(),
                    new PimsLineItemMapper()
                )
            )
        );
        PimsPayloadMapper pimsPayloadMapper = new PimsPayloadMapper(objectMapper, pimsEventMapper);
        listener = new NotificationSqsListener(queueMessageSender, objectMapper, pimsPayloadMapper, meterRegistry);
    }

    @Test
    void receive_shouldForwardToAsb_whenValid() {
        // The first publish arg is the re-serialised PimsEventV1, not the raw body.
        listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        verify(queueMessageSender).publish(any(String.class), eq(AGGREGATE_ID), eq(DEDUP_ID));
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldForwardWithGeneratedUuid_whenDedupHeaderAbsentAndNoEventId() {
        // Since the messageId is resolved in the listener (not left to publish's own null-fallback),
        // a missing header produces a freshly minted UUID.
        listener.receive(VALID_BODY, AGGREGATE_ID, null, RECEIVE_COUNT);

        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(queueMessageSender).publish(any(String.class), eq(AGGREGATE_ID), messageIdCaptor.capture());
        assertThatCode(() -> UUID.fromString(messageIdCaptor.getValue())).doesNotThrowAnyException();
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldUseBodyEventIdAsAsbMessageId_whenPresent_evenIfDedupHeaderDiffers() {
        // The SQS dedup header can legitimately differ from the body eventId on a DLQ replay — the ASB
        // messageId must still track the body eventId so ASB-level dedup stays stable across the replay.
        listener.receive(ENVELOPED_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        verify(queueMessageSender).publish(any(String.class), eq(AGGREGATE_ID), eq(EVENT_ID));
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldTreatBlankDedupHeaderAsAbsent_andMintAGeneratedId() {
        // A blank (non-null) header must not be treated as "present" and skip UUID generation.
        listener.receive(VALID_BODY, AGGREGATE_ID, "   ", RECEIVE_COUNT);

        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(queueMessageSender).publish(any(String.class), eq(AGGREGATE_ID), messageIdCaptor.capture());
        assertThatCode(() -> UUID.fromString(messageIdCaptor.getValue())).doesNotThrowAnyException();
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldForward_whenReceiveCountHeaderAbsent() {
        listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, null);

        verify(queueMessageSender).publish(any(String.class), eq(AGGREGATE_ID), eq(DEDUP_ID));
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldPublishReSerialisedPimsPayload_notRawBody() throws Exception {
        // The raw body does not equal the PimsEventV1 output — the listener must not forward the raw body.
        listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(queueMessageSender).publish(payloadCaptor.capture(), eq(AGGREGATE_ID), eq(DEDUP_ID));
        assertThat(payloadCaptor.getValue()).isNotEqualTo(VALID_BODY);

        // Re-serialised PimsEventV1 contains aggregateId from the envelope
        ObjectMapper objectMapper = new ObjectMapper();
        var pimsNode = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(pimsNode.path("aggregateId").asText()).isEqualTo(AGGREGATE_ID);
        assertThat(pimsNode.path("eventType").asText())
            .isEqualTo("uk.gov.defra.imports.notification.NotificationSubmitted");
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
    void receive_shouldThrowNonRetryable_whenBodyContainsUnknownField() {
        // Simulates a new field added to the backend outbox not yet in OutboxEvent.
        // PimsPayloadMapper raises SqsNonRetryableException; the listener propagates it unchanged.
        String bodyWithNewField =
            "{\"aggregateId\":\"" + AGGREGATE_ID
                + "\",\"eventType\":\"uk.gov.defra.imports.notification.NotificationSubmitted\""
                + ",\"brandNewBackendField\":\"unexpected\"}";

        assertThatThrownBy(() -> listener.receive(bodyWithNewField, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsNonRetryableException.class);

        verify(queueMessageSender, never()).publish(any(), any(), any());
        assertThat(counterValue("forwarded")).isEqualTo(0.0);
    }

    @Test
    void receive_shouldThrowRetryable_onFirstAttempt_whenAsbFailsTransiently() {
        // Given
        doThrow(new SqsRetryableException("ASB down", new RuntimeException("Simulated transient failure")))
            .when(queueMessageSender).publish(any(), any(), any());

        // When / Then — no in-process retry: a single attempt, then the exception propagates so SQS
        // redelivers the message (and, after maxReceiveCount, routes it to the DLQ).
        assertThatThrownBy(() -> listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsRetryableException.class);
        verify(queueMessageSender, times(1)).publish(any(), eq(AGGREGATE_ID), eq(DEDUP_ID));
        assertThat(counterValue("forwarded")).isEqualTo(0.0);
    }

    @Test
    void receive_shouldNotRetryAndThrowNonRetryable_whenAsbFailsPermanently() {
        // Given
        doThrow(new SqsNonRetryableException("entity not found", new RuntimeException("Simulated permanent failure")))
            .when(queueMessageSender).publish(any(), any(), any());

        // When / Then — non-retryable failures are not retried: single attempt then propagate to discard.
        assertThatThrownBy(() -> listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsNonRetryableException.class);
        verify(queueMessageSender, times(1)).publish(any(), eq(AGGREGATE_ID), eq(DEDUP_ID));
        assertThat(counterValue("forwarded")).isEqualTo(0.0);
    }

    @Test
    void receive_shouldForwardNotificationSubmissionAmended() {
        String body = "{\"aggregateId\":\"" + AGGREGATE_ID
            + "\",\"eventType\":\"uk.gov.defra.imports.notification.NotificationSubmissionAmended\"}";

        listener.receive(body, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        verify(queueMessageSender).publish(any(String.class), eq(AGGREGATE_ID), eq(DEDUP_ID));
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "uk.gov.defra.trade.imports.animals.NotificationEdited",
        "uk.gov.defra.unknown.SomeInternalEvent",
        ""
    })
    void receive_shouldDropEvent_whenEventTypeIsNotWhitelisted(String eventType) {
        String body = "{\"aggregateId\":\"" + AGGREGATE_ID
            + "\",\"eventType\":\"" + eventType + "\"}";

        listener.receive(body, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        verify(queueMessageSender, never()).publish(any(), any(), any());
        assertThat(counterValue("forwarded")).isEqualTo(0.0);
    }

    private double counterValue(String outcome) {
        return meterRegistry.counter("notification.sqs.messages", "outcome", outcome).count();
    }
}
