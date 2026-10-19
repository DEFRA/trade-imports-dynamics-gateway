package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;

/**
 * List, replay-all and delete-all operations on the notification DLQ. A list is a best-effort
 * snapshot; replay-all and delete-all start asynchronous SQS bulk operations and return before they
 * finish.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqService {

    private static final int SQS_MAX_MESSAGES_PER_RECEIVE = 10;
    private static final int LIST_LONG_POLL_SECONDS = 1;

    private final SqsAsyncClient sqsAsyncClient;
    private final NotificationSqsConfig sqsConfig;
    private final ObjectMapper objectMapper;

    /**
     * Returns up to {@code limit} messages from the front of the DLQ and the queue's approximate
     * depth, then makes every returned message visible again. Repeated calls return the same front
     * until messages are removed; {@code approximateCount} above {@code messages.size()} means more
     * remain.
     *
     * @param limit the most messages to return
     * @return the messages and the approximate depth sampled before they were received
     */
    public DlqListResponse list(int limit) {
        String dlqUrl = sqsConfig.dlqUrl();
        long approximateCount = approximateCount(dlqUrl);
        List<Message> collected = new ArrayList<>();
        try {
            while (collected.size() < limit) {
                int want = Math.min(SQS_MAX_MESSAGES_PER_RECEIVE, limit - collected.size());
                List<Message> batch = receive(dlqUrl, want, LIST_LONG_POLL_SECONDS);
                if (batch.isEmpty()) {
                    break;
                }
                collected.addAll(batch);
            }
            List<DlqMessage> messages = collected.stream().map(this::toDlqMessage).toList();
            log.info("Listed {} DLQ message(s)", messages.size());
            return new DlqListResponse(messages, approximateCount);
        } finally {
            release(collected);
        }
    }

    /**
     * Starts an SQS {@code StartMessageMoveTask} that moves every DLQ message back onto its source
     * queue, and returns before the move finishes.
     */
    public void replayAll() {
        sqsAsyncClient.startMessageMoveTask(request -> request.sourceArn(sqsConfig.dlqArn())).join();
        log.info("Started DLQ replay-all move task");
    }

    /**
     * Purges the DLQ with SQS {@code PurgeQueue}. Deletion can take up to 60 seconds, so a
     * {@link #list} straight after may still return purged messages.
     */
    public void deleteAll() {
        sqsAsyncClient.purgeQueue(request -> request.queueUrl(sqsConfig.dlqUrl())).join();
        log.info("Purged DLQ");
    }

    private List<Message> receive(String dlqUrl, int maxMessages, int waitSeconds) {
        return sqsAsyncClient.receiveMessage(request -> request
                .queueUrl(dlqUrl)
                .maxNumberOfMessages(maxMessages)
                .waitTimeSeconds(waitSeconds)
                .messageSystemAttributeNames(
                    MessageSystemAttributeName.MESSAGE_GROUP_ID,
                    MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID,
                    MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
            .join()
            .messages();
    }

    private void release(List<Message> messages) {
        for (int start = 0; start < messages.size(); start += SQS_MAX_MESSAGES_PER_RECEIVE) {
            List<Message> chunk = messages.subList(
                start, Math.min(start + SQS_MAX_MESSAGES_PER_RECEIVE, messages.size()));
            List<ChangeMessageVisibilityBatchRequestEntry> entries = new ArrayList<>();
            for (int i = 0; i < chunk.size(); i++) {
                entries.add(ChangeMessageVisibilityBatchRequestEntry.builder()
                    .id(String.valueOf(i))
                    .receiptHandle(chunk.get(i).receiptHandle())
                    .visibilityTimeout(0)
                    .build());
            }
            try {
                sqsAsyncClient.changeMessageVisibilityBatch(request -> request
                        .queueUrl(sqsConfig.dlqUrl())
                        .entries(entries))
                    .join();
            } catch (RuntimeException e) {
                log.warn("Could not release a browsed DLQ batch early", e);
            }
        }
    }

    private DlqMessage toDlqMessage(Message message) {
        return new DlqMessage(
            idOf(message),
            message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID),
            message.attributes().get(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID),
            receiveCount(message),
            message.body());
    }

    private String idOf(Message message) {
        return EventEnvelope.eventId(objectMapper, message.body())
            .orElseGet(() -> message.attributes().get(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID));
    }

    private int receiveCount(Message message) {
        String value = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        return value == null ? 0 : Integer.parseInt(value);
    }

    private long approximateCount(String dlqUrl) {
        String value = sqsAsyncClient.getQueueAttributes(request -> request
                .queueUrl(dlqUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
            .join()
            .attributes()
            .get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
        return value == null ? 0L : Long.parseLong(value);
    }
}
