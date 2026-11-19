package uk.gov.defra.cdp.dynamicsgateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static uk.gov.defra.cdp.dynamicsgateway.metrics.ExternalCall.SERVICE_BUS_SEND_MESSAGE;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.durationCount;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.failureTotal;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.recordedCallCount;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.totalDurationMillis;

import com.azure.core.amqp.exception.AmqpException;
import com.azure.messaging.servicebus.ServiceBusErrorSource;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.metrics.Dependency;
import uk.gov.defra.cdp.dynamicsgateway.metrics.ExternalCallRecorder;
import uk.gov.defra.cdp.dynamicsgateway.metrics.LastCallOutcomes;

@ExtendWith(MockitoExtension.class)
class QueueMessageSenderTest {

    private static final long FIFTY_MILLIS_IN_NANOS = Duration.ofMillis(50).toNanos();

    @Mock
    private ServiceBusSenderClient senderClient;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final LastCallOutcomes lastCallOutcomes = new LastCallOutcomes();
    private final AtomicLong nanoTime = new AtomicLong();

    private QueueMessageSender queueMessageSender;

    @BeforeEach
    void setUp() {
        queueMessageSender = new QueueMessageSender(
            senderClient, new ExternalCallRecorder(meterRegistry, lastCallOutcomes, nanoTime::get));
    }

    @Test
    void publish_shouldSendMessageToServiceBus() {
        // Given
        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);

        // When
        queueMessageSender.publish("{\"aggregateId\":\"GBN-AG-26-001\",\"key\":\"value\"}", "GBN-AG-26-001");

        // Then
        verify(senderClient).sendMessage(captor.capture());
        ServiceBusMessage sent = captor.getValue();
        assertThat(sent.getBody()).hasToString("{\"aggregateId\":\"GBN-AG-26-001\",\"key\":\"value\"}");
    }

    @Test
    void publish_shouldSetContentTypeAndMessageId() {
        // Given
        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);

        // When
        queueMessageSender.publish("{\"aggregateId\":\"GBN-AG-26-001\"}", "GBN-AG-26-001");

        // Then
        verify(senderClient).sendMessage(captor.capture());
        ServiceBusMessage sent = captor.getValue();
        assertThat(sent.getContentType()).isEqualTo("application/json");
        assertThat(sent.getMessageId()).isNotBlank();
    }

    @Test
    void publish_shouldUseProvidedMessageId_whenSupplied() {
        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);

        queueMessageSender.publish("{\"key\":\"value\"}", "session-1", "event-123");

        verify(senderClient).sendMessage(captor.capture());
        assertThat(captor.getValue().getMessageId()).isEqualTo("event-123");
    }

    @Test
    void publish_shouldGenerateMessageId_whenProvidedIdIsNull() {
        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);

        queueMessageSender.publish("{\"key\":\"value\"}", "session-1", null);

        verify(senderClient).sendMessage(captor.capture());
        assertThat(captor.getValue().getMessageId()).isNotBlank();
    }

    @Test
    void publish_shouldGenerateMessageId_whenProvidedIdIsBlank() {
        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);

        queueMessageSender.publish("{\"key\":\"value\"}", "session-1", "   ");

        verify(senderClient).sendMessage(captor.capture());
        assertThat(captor.getValue().getMessageId()).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    void publish_shouldSetSessionIdFromParameter() {
        // Given
        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);

        // When
        queueMessageSender.publish("{\"aggregateId\":\"Imports.Notification.GBN-AG.GBN-AG-26-001\"}", "Imports.Notification.GBN-AG.GBN-AG-26-001");

        // Then
        verify(senderClient).sendMessage(captor.capture());
        assertThat(captor.getValue().getSessionId()).isEqualTo("Imports.Notification.GBN-AG.GBN-AG-26-001");
    }

    @Test
    void publish_shouldThrowSqsNonRetryableException_whenMessageBodyIsNull() {
        assertThatThrownBy(() -> queueMessageSender.publish(null, "session-1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessage("messageBody must not be null");
    }

    @Test
    void publish_shouldThrowSqsRetryableException_whenServiceBusExceptionIsTransient() {
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);
        doThrow(transientEx).when(senderClient).sendMessage(any());

        assertThatThrownBy(() -> queueMessageSender.publish("{\"key\":\"value\"}", "session-1"))
            .isInstanceOf(SqsRetryableException.class)
            .hasCauseInstanceOf(ServiceBusException.class);
    }

    @Test
    void publish_shouldThrowSqsNonRetryableException_whenServiceBusExceptionIsNotTransient() {
        AmqpException nonTransientCause = new AmqpException(false, "too large", null, null);
        ServiceBusException nonTransientEx = new ServiceBusException(nonTransientCause, ServiceBusErrorSource.SEND);
        doThrow(nonTransientEx).when(senderClient).sendMessage(any());

        assertThatThrownBy(() -> queueMessageSender.publish("{\"key\":\"value\"}", "session-1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasCauseInstanceOf(ServiceBusException.class);
    }

    @Test
    void publish_shouldThrowSqsRetryableException_whenSenderIsDisposed() {
        doThrow(new IllegalStateException("sender disposed")).when(senderClient).sendMessage(any());

        assertThatThrownBy(() -> queueMessageSender.publish("{\"key\":\"value\"}", "session-1"))
            .isInstanceOf(SqsRetryableException.class)
            .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void publish_shouldRecordTheSendAsOneSuccessfulCallOfTheTimeItTook_andTheServiceBusAsAvailable() {
        doAnswer(invocation -> nanoTime.addAndGet(FIFTY_MILLIS_IN_NANOS))
            .when(senderClient).sendMessage(any());

        queueMessageSender.publish("{\"key\":\"value\"}", "session-1");

        assertThat(durationCount(meterRegistry, SERVICE_BUS_SEND_MESSAGE)).isEqualTo(1);
        assertThat(totalDurationMillis(meterRegistry, SERVICE_BUS_SEND_MESSAGE)).isEqualTo(50);
        assertThat(recordedCallCount(meterRegistry, SERVICE_BUS_SEND_MESSAGE)).isEqualTo(1);
        assertThat(failureTotal(meterRegistry, SERVICE_BUS_SEND_MESSAGE)).isZero();
        assertThat(lastCallOutcomes.lastCallSucceeded(Dependency.AZURE_SERVICE_BUS)).contains(true);
    }

    @Test
    void publish_shouldRecordTheSendAsOneFailedCallOfTheTimeItTook_andTheServiceBusAsUnavailable_andThrowRetryable_whenTheSendFailsTransiently() {
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);
        doAnswer(invocation -> {
            nanoTime.addAndGet(FIFTY_MILLIS_IN_NANOS);
            throw transientEx;
        }).when(senderClient).sendMessage(any());

        assertThatThrownBy(() -> queueMessageSender.publish("{\"key\":\"value\"}", "session-1"))
            .isInstanceOf(SqsRetryableException.class);

        assertThat(durationCount(meterRegistry, SERVICE_BUS_SEND_MESSAGE)).isEqualTo(1);
        assertThat(totalDurationMillis(meterRegistry, SERVICE_BUS_SEND_MESSAGE)).isEqualTo(50);
        assertThat(failureTotal(meterRegistry, SERVICE_BUS_SEND_MESSAGE)).isEqualTo(1.0);
        assertThat(lastCallOutcomes.lastCallSucceeded(Dependency.AZURE_SERVICE_BUS)).contains(false);
    }

    @Test
    void publish_shouldThrowSqsNonRetryableException_whenUnexpectedErrorOccurs() {
        doThrow(new NullPointerException("null message")).when(senderClient).sendMessage(any());

        assertThatThrownBy(() -> queueMessageSender.publish("{\"key\":\"value\"}", "session-1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasCauseInstanceOf(NullPointerException.class);
    }
}
