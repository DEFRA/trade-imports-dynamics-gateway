package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
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
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;

@ExtendWith(MockitoExtension.class)
class NotificationSqsListenerTest {

    @Mock
    private QueueMessageSender queueMessageSender;

    private NotificationSqsListener listener;
    private MeterRegistry meterRegistry;
    private ObjectMapper objectMapper;
    private PimsPayloadMapper pimsPayloadMapper;

    private static final List<String> SCHEMA_VERSIONS = List.of("0.1.0", "0.2.0", "0.3.0", "0.4.0");
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
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        pimsPayloadMapper = PimsPayloadMappers.real(objectMapper);
        listener = new NotificationSqsListener(queueMessageSender, objectMapper, pimsPayloadMapper,
            meterRegistry);
    }

    @Test
    void receive_shouldForwardToAsb_whenValid() {
        listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        verifyEveryVersionPublished(VALID_BODY, DEDUP_ID);
        assertForwardedAndFailed(SCHEMA_VERSIONS, List.of());
    }

    @Test
    void receive_shouldForwardWithGeneratedUuid_whenDedupHeaderAbsentAndNoEventId() {
        listener.receive(VALID_BODY, AGGREGATE_ID, null, RECEIVE_COUNT);

        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(queueMessageSender, times(4)).publish(any(), eq(AGGREGATE_ID), messageIdCaptor.capture());
        List<String> messageIds = messageIdCaptor.getAllValues();
        assertThatCode(() -> UUID.fromString(messageIds.get(0))).doesNotThrowAnyException();
        assertThat(messageIds).isEqualTo(messageIdsFor(messageIds.get(0)));
        assertForwardedAndFailed(SCHEMA_VERSIONS, List.of());
    }

    @Test
    void receive_shouldUseBodyEventIdAsAsbMessageId_whenPresent_evenIfDedupHeaderDiffers() {
        listener.receive(ENVELOPED_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        verifyEveryVersionPublished(ENVELOPED_BODY, EVENT_ID);
        assertForwardedAndFailed(SCHEMA_VERSIONS, List.of());
    }

    @Test
    void receive_shouldTreatBlankDedupHeaderAsAbsent_andMintAGeneratedId() {
        listener.receive(VALID_BODY, AGGREGATE_ID, "   ", RECEIVE_COUNT);

        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(queueMessageSender, times(4)).publish(any(), eq(AGGREGATE_ID), messageIdCaptor.capture());
        List<String> messageIds = messageIdCaptor.getAllValues();
        assertThatCode(() -> UUID.fromString(messageIds.get(0))).doesNotThrowAnyException();
        assertThat(messageIds).isEqualTo(messageIdsFor(messageIds.get(0)));
        assertForwardedAndFailed(SCHEMA_VERSIONS, List.of());
    }

    @Test
    void receive_shouldForward_whenReceiveCountHeaderAbsent() {
        listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, null);

        verifyEveryVersionPublished(VALID_BODY, DEDUP_ID);
        assertForwardedAndFailed(SCHEMA_VERSIONS, List.of());
    }

    @Test
    void receive_shouldPublishReSerialisedPimsPayload_notRawBody() {
        listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(queueMessageSender).publish(payloadCaptor.capture(), eq(AGGREGATE_ID), eq(DEDUP_ID));
        assertThat(payloadCaptor.getValue()).isNotEqualTo(VALID_BODY);
        assertThat(payloadCaptor.getValue()).isEqualTo(pimsV1(VALID_BODY));
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
        assertThatThrownBy(
            () -> listener.receive("not-json", AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("not valid JSON");

        verify(queueMessageSender, never()).publish(any(), any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenBodyContainsUnknownField() {
        String bodyWithNewField =
            "{\"aggregateId\":\"" + AGGREGATE_ID
                + "\",\"eventType\":\"uk.gov.defra.imports.notification.NotificationSubmitted\""
                + ",\"brandNewBackendField\":\"unexpected\"}";

        assertThatThrownBy(
            () -> listener.receive(bodyWithNewField, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsNonRetryableException.class);

        verify(queueMessageSender, never()).publish(any(), any(), any());
        assertForwardedAndFailed(List.of(), List.of());
    }

    @Test
    void receive_shouldThrowRetryable_andCountV1AsFailed_whenAsbFailsTransiently() {
        doThrow(new SqsRetryableException("ASB down",
            new RuntimeException("Simulated transient failure")))
            .when(queueMessageSender).publish(any(), any(), any());

        assertThatThrownBy(
            () -> listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsRetryableException.class);
        verify(queueMessageSender, times(1)).publish(any(), any(), any());
        verify(queueMessageSender, times(1))
            .publish(pimsV1(VALID_BODY), AGGREGATE_ID, DEDUP_ID);
        assertForwardedAndFailed(List.of(), List.of("0.1.0"));
    }

    @Test
    void receive_shouldNotRetryAndThrowNonRetryable_whenAsbFailsPermanently() {
        doThrow(new SqsNonRetryableException("entity not found",
            new RuntimeException("Simulated permanent failure")))
            .when(queueMessageSender).publish(any(), any(), any());

        assertThatThrownBy(
            () -> listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .isInstanceOf(SqsNonRetryableException.class);
        verify(queueMessageSender, times(1)).publish(any(), any(), any());
        verify(queueMessageSender, times(1))
            .publish(pimsV1(VALID_BODY), AGGREGATE_ID, DEDUP_ID);
        assertForwardedAndFailed(List.of(), List.of("0.1.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.2.0", "0.3.0", "0.4.0"})
    void receive_shouldNotThrow_andCountOnlyThatVersionAsFailed_whenItsMappingFails(String failingVersion) {
        NotificationSqsListener listenerWithFailingMapper = listenerWhoseMapperFailsFor(failingVersion);

        assertThatCode(
            () -> listenerWithFailingMapper.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .doesNotThrowAnyException();

        assertOnlyFailed(failingVersion);
    }

    private NotificationSqsListener listenerWhoseMapperFailsFor(String version) {
        PimsPayloadMapper failingMapper = spy(pimsPayloadMapper);
        IllegalStateException failure = new IllegalStateException("Cannot map to PIMS v" + version);
        switch (version) {
            case "0.2.0" -> doThrow(failure).when(failingMapper).mapToV2(any());
            case "0.3.0" -> doThrow(failure).when(failingMapper).mapToV3(any());
            case "0.4.0" -> doThrow(failure).when(failingMapper).mapToV4(any());
            default -> throw new IllegalArgumentException("No mapper for version " + version);
        }
        return new NotificationSqsListener(queueMessageSender, objectMapper, failingMapper, meterRegistry);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.2.0", "0.3.0", "0.4.0"})
    void receive_shouldForwardEveryOtherVersionAndCountOnlyThatVersionAsFailed_whenItsPublishFails(
        String failingVersion) {
        lenient().doThrow(new SqsNonRetryableException("boom", new RuntimeException("simulated")))
            .when(queueMessageSender)
            .publish(any(), eq(AGGREGATE_ID), eq(messageIdFor(DEDUP_ID, failingVersion)));

        assertThatCode(() -> listener.receive(VALID_BODY, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT))
            .doesNotThrowAnyException();

        for (String version : SCHEMA_VERSIONS) {
            if (!version.equals(failingVersion)) {
                verify(queueMessageSender).publish(payloadFor(VALID_BODY, version), AGGREGATE_ID,
                    messageIdFor(DEDUP_ID, version));
            }
        }
        assertOnlyFailed(failingVersion);
    }

    @Test
    void receive_shouldForwardNotificationSubmissionAmended() {
        String body = "{\"aggregateId\":\"" + AGGREGATE_ID
            + "\",\"eventType\":\"uk.gov.defra.imports.notification.NotificationSubmissionAmended\"}";

        listener.receive(body, AGGREGATE_ID, DEDUP_ID, RECEIVE_COUNT);

        verifyEveryVersionPublished(body, DEDUP_ID);
        assertForwardedAndFailed(SCHEMA_VERSIONS, List.of());
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
        assertForwardedAndFailed(List.of(), List.of());
    }

    private void verifyEveryVersionPublished(String body, String baseMessageId) {
        for (String version : SCHEMA_VERSIONS) {
            verify(queueMessageSender).publish(payloadFor(body, version), AGGREGATE_ID,
                messageIdFor(baseMessageId, version));
        }
    }

    private String payloadFor(String body, String version) {
        return switch (version) {
            case "0.1.0" -> pimsV1(body);
            case "0.2.0" -> pims(body, pimsPayloadMapper::mapToV2);
            case "0.3.0" -> pims(body, pimsPayloadMapper::mapToV3);
            case "0.4.0" -> pims(body, pimsPayloadMapper::mapToV4);
            default -> throw new IllegalArgumentException("No payload for version " + version);
        };
    }

    private static String messageIdFor(String baseMessageId, String version) {
        int index = SCHEMA_VERSIONS.indexOf(version);
        return index == 0 ? baseMessageId : baseMessageId + "-v" + (index + 1);
    }

    private static List<String> messageIdsFor(String baseMessageId) {
        return SCHEMA_VERSIONS.stream().map(version -> messageIdFor(baseMessageId, version)).toList();
    }

    private void assertOnlyFailed(String failingVersion) {
        assertForwardedAndFailed(
            SCHEMA_VERSIONS.stream().filter(version -> !version.equals(failingVersion)).toList(),
            List.of(failingVersion));
    }

    private void assertForwardedAndFailed(List<String> forwardedVersions, List<String> failedVersions) {
        for (String version : SCHEMA_VERSIONS) {
            assertThat(counterValue("forwarded", version))
                .as("forwarded " + version)
                .isEqualTo(forwardedVersions.contains(version) ? 1.0 : 0.0);
            assertThat(failedCount(version))
                .as("failed " + version)
                .isEqualTo(failedVersions.contains(version) ? 1.0 : 0.0);
        }
    }

    private String pimsV1(String rawBody) {
        try {
            return pimsPayloadMapper.mapToV1(pimsPayloadMapper.parse(objectMapper.readTree(rawBody)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute expected v0.1.0 PIMS payload for: " + rawBody,
                e);
        }
    }

    private String pims(String rawBody, Function<OutboxEvent, String> mapToPayload) {
        try {
            return mapToPayload.apply(pimsPayloadMapper.parse(objectMapper.readTree(rawBody)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute expected PIMS payload for: " + rawBody, e);
        }
    }

    private double counterValue(String outcome, String schemaVersion) {
        return meterRegistry.counter("notification.message", "outcome", outcome,
            "schema.version", schemaVersion).count();
    }

    private double failedCount(String schemaVersion) {
        return counterValue("failed", schemaVersion);
    }
}
