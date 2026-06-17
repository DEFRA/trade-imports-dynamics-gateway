package uk.gov.defra.cdp.dynamicsgateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.DynamicsGatewayException;

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
    void publish_shouldThrowGatewayException_whenSendFails() {
        // Given
        doThrow(new RuntimeException("ASB connection refused")).when(senderClient).sendMessage(any());

        // When & Then
        assertThatThrownBy(() -> queueMessageSender.publish("{\"aggregateId\":\"GBN-AG-26-001\"}", "GBN-AG-26-001"))
            .isInstanceOf(DynamicsGatewayException.class)
            .hasMessageContaining("Failed to send event to Azure Service Bus");
    }
}
