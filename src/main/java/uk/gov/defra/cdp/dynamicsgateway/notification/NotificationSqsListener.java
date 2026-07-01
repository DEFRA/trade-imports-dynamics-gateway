package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.support.RetryTemplate;
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
    private final RetryTemplate retryTemplate;
    private final Counter forwardedCounter;

    public NotificationSqsListener(
            QueueMessageSender queueMessageSender,
            ObjectMapper objectMapper,
            RetryTemplate notificationRetryTemplate,
            MeterRegistry meterRegistry) {
        this.queueMessageSender = queueMessageSender;
        this.objectMapper = objectMapper;
        this.retryTemplate = notificationRetryTemplate;
        this.forwardedCounter = Counter.builder("notification.sqs.messages")
            .tag("outcome", "forwarded")
            .description("Messages successfully forwarded to ASB")
            .register(meterRegistry);
    }

    @SqsListener("${aws.sqs.notification.queue-url}")
    public void receive(
            String body,
            @Header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER) String aggregateId,
            @Header(name = SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER,
                required = false) String deduplicationId,
            // SQS ApproximateReceiveCount — climbs 1, 2, 3... on each redelivery. Logged so a failing
            // message's progress toward the DLQ is visible: once it hits the queue's maxReceiveCount and
            // fails again, SQS (not this app) moves it to the DLQ — there is no gateway "sent to DLQ" log.
            @Header(name = SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT,
                required = false) String receiveCount) {

        log.info("Received notification event from SQS: aggregateId={}, deduplicationId={}, receiveCount={}, bodyLength={}",
            aggregateId, deduplicationId, receiveCount, body != null ? body.length() : 0);

        if (aggregateId == null || aggregateId.isBlank()) {
            throw new SqsNonRetryableException("Missing or blank MESSAGE_GROUP_ID: " + aggregateId);
        }

        if (body == null || body.isBlank()) {
            throw new SqsNonRetryableException("Empty message body");
        }

        try {
            objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new SqsNonRetryableException("Message body is not valid JSON", e);
        }

        String asbMessageId = resolveAsbMessageId(body, deduplicationId);
        publishWithRetry(body, aggregateId, asbMessageId);
        forwardedCounter.increment();
    }

    /**
     * Resolve the ASB messageId once per message — before {@link #publishWithRetry} enters the retry
     * loop — so every in-process retry attempt of the same message reuses the identical id. Resolving
     * it per attempt instead (e.g. leaving {@link QueueMessageSender#publish} to mint a fallback UUID on
     * each call) would let a "transient" failure that was actually a false negative (ASB durably
     * received the message; only the ack was lost) retry under a different id each time, defeating ASB
     * duplicate detection if it is ever enabled — see {@code docs/notification-pipeline-dedup.md}.
     *
     * <p>Precedence: body {@code eventId} — deliberately preferred over the SQS header, since
     * {@link DlqService} mints a fresh, unique transport dedup id on every replay, so the header can
     * differ between a message's original delivery and a later replay of it, but the ASB messageId must
     * stay equal to the original {@code eventId} across both — then the non-blank SQS dedup header, then
     * a single freshly-generated UUID if neither is present.
     */
    private String resolveAsbMessageId(String body, String deduplicationId) {
        return EventEnvelope.eventId(objectMapper, body)
            .or(() -> Optional.ofNullable(deduplicationId).filter(id -> !id.isBlank()))
            .orElseGet(() -> UUID.randomUUID().toString());
    }

    /**
     * A transient ASB failure is retried in-process with exponential backoff (the retry template only
     * retries {@code SqsRetryableException}); on exhaustion it propagates so SQS redelivers, then DLQs.
     */
    private void publishWithRetry(String body, String aggregateId, String asbMessageId) {
        retryTemplate.execute(_ -> {
            queueMessageSender.publish(body, aggregateId, asbMessageId);
            return null;
        });
    }
}
