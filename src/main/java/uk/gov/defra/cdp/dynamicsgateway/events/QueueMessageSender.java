package uk.gov.defra.cdp.dynamicsgateway.events;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.DynamicsGatewayException;

/**
 * Forwards events to Azure Service Bus (ASB).
 *
 * <p>Throws {@link DynamicsGatewayException} on ASB send failure (network, throttle, broker
 * unavailable). When called from
 * {@link uk.gov.defra.cdp.dynamicsgateway.notification.NotificationSqsListener}, this exception
 * propagates out of {@code @SqsListener} — Spring Cloud AWS leaves the message in the queue
 * for retry, eventually routing to the DLQ after {@code maxReceiveCount}.
 *
 * <p>Callers are responsible for validating inputs (sessionId, body) before calling
 * {@link #publish}. The SQS listener validates aggregateId and JSON before forwarding;
 * the REST controller should validate similarly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueMessageSender {

    private final ServiceBusSenderClient senderClient;

    /**
     * Send a pre-serialised message to ASB on the given session.
     *
     * @param messageBody the event payload as a JSON string (pre-validated and serialised by caller)
     * @param sessionId   ASB session ID (pre-validated by caller); must not be blank
     * @throws DynamicsGatewayException if the ASB send fails (retryable)
     */
    public void publish(String messageBody, String sessionId) {
        try {
            String messageId = UUID.randomUUID().toString();
            ServiceBusMessage message = new ServiceBusMessage(messageBody)
                .setMessageId(messageId)
                .setContentType("application/json")
                .setSessionId(sessionId);
            senderClient.sendMessage(message);
            log.info("Event forwarded to Azure Service Bus, messageId={}, sessionId={}", messageId, sessionId);
        } catch (Exception e) {
            log.error("Failed to forward event to Azure Service Bus: {}", e.getMessage(), e);
            throw new DynamicsGatewayException("Failed to send event to Azure Service Bus", e);
        }
    }
}
