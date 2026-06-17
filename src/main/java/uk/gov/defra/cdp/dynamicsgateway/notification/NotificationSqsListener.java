package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender;

/**
 * Consumes notification events from an SQS FIFO queue and forwards them to Azure Service Bus.
 *
 * <p>Spring Cloud AWS manages the polling lifecycle and message deletion:
 * <ul>
 *   <li><strong>Normal return</strong> — message is acknowledged (deleted from SQS). Used for
 *       successful forwards and non-retryable input errors (missing aggregateId, invalid JSON).</li>
 *   <li><strong>Thrown exception</strong> — routed to {@link NotificationErrorHandler}, which
 *       classifies the error as retryable (left for retry → DLQ) or non-retryable (deleted).</li>
 * </ul>
 */
@Slf4j
@Component
public class NotificationSqsListener {

    private final QueueMessageSender queueMessageSender;
    private final ObjectMapper objectMapper;
    private final Counter forwardedCounter;
    private final Counter discardedMissingIdCounter;
    private final Counter discardedInvalidJsonCounter;

    public NotificationSqsListener(
            QueueMessageSender queueMessageSender,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.queueMessageSender = queueMessageSender;
        this.objectMapper = objectMapper;
        this.forwardedCounter = Counter.builder("notification.sqs.messages")
            .tag("outcome", "forwarded")
            .description("Messages successfully forwarded to ASB")
            .register(meterRegistry);
        this.discardedMissingIdCounter = Counter.builder("notification.sqs.messages")
            .tag("outcome", "discarded_missing_id")
            .description("Messages discarded due to missing or blank MESSAGE_GROUP_ID")
            .register(meterRegistry);
        this.discardedInvalidJsonCounter = Counter.builder("notification.sqs.messages")
            .tag("outcome", "discarded_invalid_json")
            .description("Messages discarded due to invalid JSON body")
            .register(meterRegistry);
    }

    @SqsListener("${aws.sqs.notification.queue-url}")
    public void receive(
            String body,
            @Header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER) String aggregateId) {

        // Non-retryable: missing routing key — return normally so Spring Cloud AWS deletes the message
        if (aggregateId == null || aggregateId.isBlank()) {
            discardedMissingIdCounter.increment();
            log.error("SQS message has missing or blank MESSAGE_GROUP_ID, skipping (auto-deleted): body={}", body);
            return;
        }

        // Non-retryable: unparseable payload — return normally so Spring Cloud AWS deletes the message
        if (!isValidJson(body)) {
            discardedInvalidJsonCounter.increment();
            log.error("SQS message body is not valid JSON, skipping (auto-deleted): body={}", body);
            return;
        }

        // On failure, the exception propagates to NotificationErrorHandler which classifies
        // it as retryable (left in queue) or non-retryable (deleted)
        queueMessageSender.publish(body, aggregateId);
        forwardedCounter.increment();
        log.info("Event forwarded to ASB: aggregateId={}", aggregateId);
    }

    private boolean isValidJson(String body) {
        try {
            objectMapper.readTree(body);
            return true;
        } catch (JsonProcessingException _) {
            return false;
        }
    }
}
