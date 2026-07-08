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

/**
 * Forwards events to Azure Service Bus (ASB), classifying send failures as
 * {@link SqsRetryableException} (transient {@link ServiceBusException}, or a sender-disposed
 * {@link IllegalStateException}) or {@link SqsNonRetryableException} (non-transient
 * {@link ServiceBusException}, or an unexpected error).
 *
 * <p>{@link uk.gov.defra.cdp.dynamicsgateway.notification.NotificationErrorHandler} uses that
 * classification to decide whether to retry (leave in SQS) or discard (delete from SQS).
 *
 * <p>Callers are responsible for validating inputs (sessionId, body) before calling
 * {@link #publish}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueMessageSender {

    private final ServiceBusSenderClient senderClient;

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
     * @param messageId   stable id to set as the ASB messageId (falls back to a fresh UUID when null
     *                    or blank). Keeping it stable end-to-end is what makes ASB duplicate detection
     *                    effective if it's ever enabled — see the workspace-local
     *                    {@code docs/notification-pipeline-dedup.md} (untracked, not part of this
     *                    repo) for the full write-up.
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
            senderClient.sendMessage(message);
            log.info("Event forwarded to Azure Service Bus, messageId={}, sessionId={}", resolvedMessageId, sessionId);
        } catch (ServiceBusException e) {
            classifyAndThrow(e, resolvedMessageId, sessionId);
        } catch (IllegalStateException e) {
            log.warn("ASB sender in illegal state ({}), retryable: {}", e.getClass().getSimpleName(), e.getMessage());
            throw new SqsRetryableException("ASB sender disposed", e);
        } catch (Exception e) {
            log.error("Unexpected error ({}) sending to ASB, non-retryable: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            throw new SqsNonRetryableException("Unexpected ASB send failure", e);
        }
    }

    private void classifyAndThrow(ServiceBusException e, String messageId, String sessionId) {
        if (e.isTransient()) {
            log.warn("Transient ASB error (reason={}, messageId={}, sessionId={}): {}",
                e.getReason(), messageId, sessionId, e.getMessage());
            throw new SqsRetryableException("Transient ASB send failure", e);
        }
        log.error("Non-transient ASB error (reason={}, messageId={}, sessionId={}): {}",
            e.getReason(), messageId, sessionId, e.getMessage(), e);
        throw new SqsNonRetryableException("Non-transient ASB send failure", e);
    }
}
