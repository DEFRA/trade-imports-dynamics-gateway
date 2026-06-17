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
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;

/**
 * Consumes notification events from an SQS FIFO queue and forwards them to Azure Service Bus.
 *
 * <p>All exceptions are routed to {@link NotificationErrorHandler}, which classifies them as
 * retryable (left in SQS for retry → DLQ) or non-retryable (deleted from SQS).
 */
@Slf4j
@Component
public class NotificationSqsListener {

    private final QueueMessageSender queueMessageSender;
    private final ObjectMapper objectMapper;
    private final Counter forwardedCounter;

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
    }

    @SqsListener("${aws.sqs.notification.queue-url}")
    public void receive(
            String body,
            @Header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER) String aggregateId) {

        if (aggregateId == null || aggregateId.isBlank()) {
            throw new SqsNonRetryableException("Missing or blank MESSAGE_GROUP_ID", null);
        }

        if (!isValidJson(body)) {
            throw new SqsNonRetryableException("Message body is not valid JSON", null);
        }

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
