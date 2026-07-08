package uk.gov.defra.cdp.dynamicsgateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.azure.core.amqp.exception.AmqpException;
import com.azure.messaging.servicebus.ServiceBusErrorSource;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsRetryableException;

@ExtendWith(MockitoExtension.class)
class QueueMessageSenderTest {

    @Mock
    private ServiceBusSenderClient senderClient;

    private QueueMessageSender queueMessageSender;

    @BeforeEach
    void setUp() {
        queueMessageSender = new QueueMessageSender(senderClient);
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
    void publish_shouldThrowSqsNonRetryableException_whenUnexpectedErrorOccurs() {
        doThrow(new NullPointerException("null message")).when(senderClient).sendMessage(any());

        assertThatThrownBy(() -> queueMessageSender.publish("{\"key\":\"value\"}", "session-1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasCauseInstanceOf(NullPointerException.class);
    }
}
