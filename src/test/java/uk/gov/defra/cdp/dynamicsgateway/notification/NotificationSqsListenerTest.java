package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;
import uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.DynamicsGatewayException;

@ExtendWith(MockitoExtension.class)
class NotificationSqsListenerTest {

    @Mock
    private SqsClient sqsClient;
    @Mock
    private QueueMessageSender queueMessageSender;

    private NotificationSqsListener listener;
    private ObjectMapper objectMapper;

    private static final String QUEUE_URL = "http://localhost:4566/000000000000/trade_imports_animals_eu_notifications_gateway.fifo";
    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        NotificationSqsConfig config = new NotificationSqsConfig(QUEUE_URL, 30, 20, 10);
        listener = new NotificationSqsListener(sqsClient, queueMessageSender, config, objectMapper);
    }

    @Test
    void pollOnce_shouldForwardMessageAndDelete_whenValid() throws Exception {
        // Given
        String body = "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";
        Message message = messageWith(AGGREGATE_ID, body, "receipt-1");
        stubReceive(message);

        // When
        listener.pollOnce();

        // Then
        verify(queueMessageSender).publish(any(), eq(AGGREGATE_ID));
        verify(sqsClient).deleteMessage(any(java.util.function.Consumer.class));
    }

    @Test
    void pollOnce_shouldDeleteWithoutForwarding_whenMessageGroupIdMissing() throws Exception {
        // Given
        Message message = Message.builder()
            .messageId("msg-2")
            .body("{\"aggregateId\":\"" + AGGREGATE_ID + "\"}")
            .receiptHandle("receipt-2")
            .attributes(Map.of())
            .build();
        stubReceive(message);

        // When
        listener.pollOnce();

        // Then
        verify(queueMessageSender, never()).publish(any(), any());
        verify(sqsClient).deleteMessage(any(java.util.function.Consumer.class));
    }

    @Test
    void pollOnce_shouldDeleteWithoutForwarding_whenBodyIsInvalidJson() throws Exception {
        // Given
        Message message = messageWith(AGGREGATE_ID, "not-json", "receipt-3");
        stubReceive(message);

        // When
        listener.pollOnce();

        // Then
        verify(queueMessageSender, never()).publish(any(), any());
        verify(sqsClient).deleteMessage(any(java.util.function.Consumer.class));
    }

    @Test
    void pollOnce_shouldLeaveMessageInQueue_whenAsbThrows() throws Exception {
        // Given
        String body = "{\"aggregateId\":\"" + AGGREGATE_ID + "\"}";
        Message message = messageWith(AGGREGATE_ID, body, "receipt-4");
        stubReceive(message);
        doThrow(new DynamicsGatewayException("ASB unavailable")).when(queueMessageSender).publish(any(), any());

        // When
        listener.pollOnce();

        // Then
        verify(sqsClient, never()).deleteMessage(any(java.util.function.Consumer.class));
    }

    @Test
    void pollOnce_shouldProcessNothing_whenQueueIsEmpty() {
        // Given
        when(sqsClient.receiveMessage(any(java.util.function.Consumer.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        // When
        listener.pollOnce();

        // Then
        verify(queueMessageSender, never()).publish(any(), any());
        verify(sqsClient, never()).deleteMessage(any(java.util.function.Consumer.class));
    }

    private Message messageWith(String aggregateId, String body, String receiptHandle) {
        return Message.builder()
            .messageId("msg-" + receiptHandle)
            .body(body)
            .receiptHandle(receiptHandle)
            .attributes(Map.of(MessageSystemAttributeName.MESSAGE_GROUP_ID, aggregateId))
            .build();
    }

    private void stubReceive(Message message) {
        when(sqsClient.receiveMessage(any(java.util.function.Consumer.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of(message)).build());
    }
}