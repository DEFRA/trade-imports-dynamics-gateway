package uk.gov.defra.cdp.dynamicsgateway.events;

import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.metrics.ExternalCall;
import uk.gov.defra.cdp.dynamicsgateway.metrics.ExternalCallMetrics;

/**
 * Forwards events to Azure Service Bus (ASB), classifying send failures as
 * {@link SqsRetryableException} (transient {@link ServiceBusException}, or a sender-disposed
 * {@link IllegalStateException}) or {@link SqsNonRetryableException} (non-transient
 * {@link ServiceBusException}, or an unexpected error).
 *
 * <p>The session id is not validated here. Every send is measured as
 * {@code azure-service-bus}/{@code send-message}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueMessageSender {

    private final ServiceBusSenderClient senderClient;
    private final ExternalCallMetrics externalCallMetrics;

    /**
     * Send a pre-serialised message to ASB on the given session, generating a fresh ASB messageId.
     *
     * <p>Use {@link #publish(String, String, String)} to carry a stable upstream id (e.g. the SQS
     * MessageDeduplicationId) when one is available.
     *
     * @param messageBody the event payload as a JSON string (pre-validated and serialised by caller)
     * @param sessionId   ASB session ID (pre-validated by caller); must not be blank
     * @throws SqsRetryableException if the failure is transient and worth retrying
     * @throws SqsNonRetryableException if the failure is permanent and retrying will not help
     */
    public void publish(String messageBody, String sessionId) {
        publish(messageBody, sessionId, null);
    }

    /**
     * Send a pre-serialised message to ASB on the given session.
     *
     * @param messageBody the event payload as a JSON string (pre-validated and serialised by caller)
     * @param sessionId   ASB session ID (pre-validated by caller); must not be blank
     * @param messageId   the ASB messageId; a fresh UUID when null or blank
     * @throws SqsRetryableException if the failure is transient and worth retrying
     * @throws SqsNonRetryableException if the failure is permanent and retrying will not help
     */
    public void publish(String messageBody, String sessionId, String messageId) {
        if (messageBody == null) {
            throw new SqsNonRetryableException("messageBody must not be null");
        }
        String resolvedMessageId = (messageId != null && !messageId.isBlank())
            ? messageId
            : UUID.randomUUID().toString();
        ServiceBusMessage message = new ServiceBusMessage(messageBody)
            .setMessageId(resolvedMessageId)
            .setContentType("application/json")
            .setSessionId(sessionId);

        try {
            externalCallMetrics.measure(
                ExternalCall.SERVICE_BUS_SEND_MESSAGE, () -> senderClient.sendMessage(message));
            log.info("Event forwarded to Azure Service Bus, messageId={}, sessionId={}", resolvedMessageId, sessionId);
        } catch (ServiceBusException e) {
            classifyAndThrow(e, resolvedMessageId, sessionId);
        } catch (IllegalStateException e) {
            log.warn("ASB sender in illegal state, retryable (messageId={}, sessionId={})",
                resolvedMessageId, sessionId, e);
            throw new SqsRetryableException("ASB sender disposed", e);
        } catch (Exception e) {
            log.error("Unexpected error sending to ASB, non-retryable (messageId={}, sessionId={})",
                resolvedMessageId, sessionId, e);
            throw new SqsNonRetryableException("Unexpected ASB send failure", e);
        }
    }

    private void classifyAndThrow(ServiceBusException e, String messageId, String sessionId) {
        if (e.isTransient()) {
            log.warn("Transient ASB error, retryable (reason={}, messageId={}, sessionId={})",
                e.getReason(), messageId, sessionId, e);
            throw new SqsRetryableException("Transient ASB send failure", e);
        }
        log.error("Non-transient ASB error, non-retryable (reason={}, messageId={}, sessionId={})",
            e.getReason(), messageId, sessionId, e);
        throw new SqsNonRetryableException("Non-transient ASB send failure", e);
    }
}
