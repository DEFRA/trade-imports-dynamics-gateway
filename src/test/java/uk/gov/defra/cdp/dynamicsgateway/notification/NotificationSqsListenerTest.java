package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private static final String VALID_BODY =
        "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new NotificationSqsListener(queueMessageSender, new ObjectMapper(), meterRegistry);
    }

    @Test
    void receive_shouldForwardToAsb_whenValid() {
        listener.receive(VALID_BODY, AGGREGATE_ID);

        verify(queueMessageSender).publish(eq(VALID_BODY), eq(AGGREGATE_ID));
        assertThat(counterValue("forwarded")).isEqualTo(1.0);
    }

    @Test
    void receive_shouldThrowNonRetryable_whenAggregateIdIsNull() {
        assertThatThrownBy(() -> listener.receive(VALID_BODY, null))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("MESSAGE_GROUP_ID");

        verify(queueMessageSender, never()).publish(any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenAggregateIdIsBlank() {
        assertThatThrownBy(() -> listener.receive(VALID_BODY, "  "))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("MESSAGE_GROUP_ID");

        verify(queueMessageSender, never()).publish(any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenBodyIsNull() {
        assertThatThrownBy(() -> listener.receive(null, AGGREGATE_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessage("Empty message body");

        verify(queueMessageSender, never()).publish(any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenBodyIsBlank() {
        assertThatThrownBy(() -> listener.receive("  ", AGGREGATE_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessage("Empty message body");

        verify(queueMessageSender, never()).publish(any(), any());
    }

    @Test
    void receive_shouldThrowNonRetryable_whenBodyIsInvalidJson() {
        assertThatThrownBy(() -> listener.receive("not-json", AGGREGATE_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("not valid JSON");

        verify(queueMessageSender, never()).publish(any(), any());
    }

    @Test
    void receive_shouldThrowRetryable_whenAsbFailsTransiently() {
        doThrow(new SqsRetryableException("ASB down", new RuntimeException("Simulated transient failure")))
            .when(queueMessageSender).publish(any(), any());

        assertThatThrownBy(() -> listener.receive(VALID_BODY, AGGREGATE_ID))
            .isInstanceOf(SqsRetryableException.class);
        assertThat(counterValue("forwarded")).isEqualTo(0.0);
    }

    @Test
    void receive_shouldThrowNonRetryable_whenAsbFailsPermanently() {
        doThrow(new SqsNonRetryableException("entity not found", new RuntimeException("Simulated permanent failure")))
            .when(queueMessageSender).publish(any(), any());

        assertThatThrownBy(() -> listener.receive(VALID_BODY, AGGREGATE_ID))
            .isInstanceOf(SqsNonRetryableException.class);
        assertThat(counterValue("forwarded")).isEqualTo(0.0);
    }

    private double counterValue(String outcome) {
        return meterRegistry.counter("notification.sqs.messages", "outcome", outcome).count();
    }
}
