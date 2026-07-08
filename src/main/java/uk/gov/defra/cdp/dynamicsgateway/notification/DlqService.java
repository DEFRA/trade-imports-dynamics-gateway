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
 * List, replay-all and delete-all operations on the notification DLQ.
 *
 * <p>SQS has no get-by-id or stable cursor: messages are found by re-receiving batches (≤10 per call),
 * and {@code GetQueueAttributes} counts are eventually consistent, so a list is a best-effort
 * snapshot. A receive hides a message for the visibility timeout, so a browse scans with a short lock
 * and then {@link #release releases} every message immediately, so the browse is non-destructive.
 *
 * <p>Replay-all and delete-all are native SQS bulk operations ({@code StartMessageMoveTask} /
 * {@code PurgeQueue}) rather than per-message plumbing — there is no selective replay/delete of the
 * DLQ, only "move everything back" or "wipe everything". Both are asynchronous: AWS completes the
 * work in the background after the call returns, and each allows only one in-flight operation per
 * queue (a second call while one is running fails — surfaced as a 502 by
 * {@link uk.gov.defra.cdp.dynamicsgateway.exceptions.GlobalExceptionHandler}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqService {

    /** SQS hard cap on messages per receive call. */
    private static final int MAX_RECEIVE = 10;

    /** Long-poll wait for list paging so a page isn't cut short by SQS short-poll sampling. */
    private static final int LIST_WAIT_SECONDS = 1;

    private final SqsAsyncClient sqsAsyncClient;
    private final NotificationSqsConfig sqsConfig;
    private final ObjectMapper objectMapper;

    /**
     * Browse the front of the DLQ — up to {@code limit} messages plus the queue's approximate depth.
     * SQS returns ≤10 per receive, so a larger page is assembled by receiving 10 at a time (holding
     * each batch in-flight to page past it) until {@code limit} is reached or the queue yields nothing
     * more; whichever comes first. Every browsed message is then {@link #release released} back, so the
     * browse is non-destructive — {@code limit} is honoured as-is, with no artificial cap.
     *
     * <p>There is no cursor: SQS always reads from the front, so a second call returns the same front
     * unless messages have been removed. {@code approximateCount} greater than {@code messages.size()}
     * is the signal that more remain.
     */
    public DlqListResponse list(int limit) {
        String dlqUrl = sqsConfig.dlqUrl();
        // Sample the depth before browsing: receiving holds messages in-flight, and
        // ApproximateNumberOfMessages counts only visible messages, so reading it after the browse
        // would deflate the count by exactly the page just received.
        long approximateCount = approximateCount(dlqUrl);
        List<Message> collected = new ArrayList<>();
        try {
            while (collected.size() < limit) {
                int want = Math.min(MAX_RECEIVE, limit - collected.size());
                List<Message> batch = receive(dlqUrl, want, LIST_WAIT_SECONDS);
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
     * Move every DLQ message back onto its source queue via {@code StartMessageMoveTask}. No
     * destination is specified — it defaults to the source queue via the DLQ's redrive-allow-policy,
     * which is how the CDP-provisioned queues are set up. Fire-and-forget: AWS runs the move in the
     * background and this call only starts it.
     */
    public void replayAll() {
        sqsAsyncClient.startMessageMoveTask(request -> request.sourceArn(sqsConfig.dlqArn())).join();
        log.info("Started DLQ replay-all move task");
    }

    /**
     * Wipe the DLQ via {@code PurgeQueue}. Asynchronous and approximate: per the AWS API reference,
     * deletion can take up to 60 seconds, so a {@link #list} called immediately after may still show
     * messages already slated for removal.
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

    /**
     * Make every scanned message visible again so a following request can see it.
     * Released in batches of ≤10 ({@code changeMessageVisibilityBatch}); best-effort, since a lapsed
     * receipt handle just means the message is already visible.
     */
    private void release(List<Message> messages) {
        for (int start = 0; start < messages.size(); start += MAX_RECEIVE) {
            List<Message> chunk = messages.subList(start, Math.min(start + MAX_RECEIVE, messages.size()));
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
                log.warn("Could not release a browsed DLQ batch early: {}", e.getMessage());
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

    /**
     * The stable id shown to the operator: the {@code eventId} from the body, otherwise the SQS
     * {@code MessageDeduplicationId}. This is the operator-facing id ({@link DlqMessage#id()}) and the
     * ASB {@code messageId} ({@link NotificationSqsListener} derives it the same way).
     */
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
