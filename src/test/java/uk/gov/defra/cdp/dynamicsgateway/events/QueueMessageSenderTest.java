package uk.gov.defra.cdp.dynamicsgateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        queueMessageSender = new QueueMessageSender(senderClient, objectMapper);
    }

    @Test
    void publish_shouldSendMessageToServiceBus() throws Exception {
        // Given
        JsonNode body = objectMapper.readTree("{\"key\":\"value\"}");
        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);

        // When
        queueMessageSender.publish(body);

        // Then
        verify(senderClient).sendMessage(captor.capture());
        ServiceBusMessage sent = captor.getValue();
        assertThat(sent.getBody().toString()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void publish_shouldSetContentTypeAndMessageId() throws Exception {
        // Given
        JsonNode body = objectMapper.readTree("{}");
        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);

        // When
        queueMessageSender.publish(body);

        // Then
        verify(senderClient).sendMessage(captor.capture());
        ServiceBusMessage sent = captor.getValue();
        assertThat(sent.getContentType()).isEqualTo("application/json");
        assertThat(sent.getMessageId()).isNotBlank();
    }

    @Test
    void publish_shouldThrowGatewayException_whenSendFails() throws Exception {
        // Given
        JsonNode body = objectMapper.readTree("{}");
        doThrow(new RuntimeException("ASB connection refused")).when(senderClient).sendMessage(any());

        // When & Then
        assertThatThrownBy(() -> queueMessageSender.publish(body))
            .isInstanceOf(DynamicsGatewayException.class)
            .hasMessageContaining("Failed to send event to Azure Service Bus");
    }
}
