package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;

/**
 * List, replay and delete messages on the notification DLQ.
 *
 * <p>SQS has no get-by-id or stable cursor: messages are found by re-receiving batches (≤10 per call)
 * and matching on {@link #idOf(Message) their id}, and {@code GetQueueAttributes} counts are
 * eventually consistent, so a list is a best-effort snapshot. A receive hides a message for the
 * visibility timeout, so an operation scans with a short lock and then {@link #release releases} every
 * message it received but did not action — targets of a delete/replay are removed, everything else is
 * made visible again immediately so a following request can still see it. Messages are only ever
 * removed individually by receipt handle, never {@code purgeQueue}.
 *
 * <p>Each received batch (≤10) maps to one SQS batch call — {@code sendMessageBatch} /
 * {@code deleteMessageBatch} for replay/delete, {@code changeMessageVisibilityBatch} to release. Batch
 * calls report per-entry success/failure rather than throwing, so replay only deletes from the DLQ the
 * messages that actually re-sent. Ids not seen within the bounded receive window (e.g. blocked behind
 * an in-flight FIFO group) are logged and left on the DLQ for the caller to retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqService {

    /** SQS hard cap on messages per receive call, and per batch action. */
    private static final int MAX_RECEIVE = 10;

    /** Upper bound on receive calls per scan so a blocked FIFO group can't loop forever. */
    private static final int MAX_RECEIVE_POLLS = 10;

    /** Long-poll wait for list paging so a page isn't cut short by SQS short-poll sampling. */
    private static final int LIST_WAIT_SECONDS = 1;

    /** Short visibility lock while browsing/scanning; messages not actioned are released back to 0 after. */
    private static final int SCAN_VISIBILITY_SECONDS = 30;

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
     * unless messages have been removed. Callers therefore <em>advance by draining</em> — replay/delete
     * the messages shown, then list again; the actioned ones are gone so the front has moved on, and
     * for a FIFO group the next message surfaces only once its predecessor is removed.
     * {@code approximateCount} greater than {@code messages.size()} is the signal that more remain.
     */
    public DlqListResponse list(int limit) {
        String dlqUrl = requireDlqUrl();
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
     * Re-send each selected message to the source queue (preserving its FIFO group) then delete it from
     * the DLQ. The re-send happens before the delete, and only messages that actually re-sent are
     * deleted, so a failed publish leaves the message on the DLQ. Ids not found within the receive window
     * are logged and left for the caller to retry.
     *
     * <p>The re-send deliberately does NOT reuse the message's original source-queue dedup id — see
     * {@link #replayBatch} and {@code docs/notification-pipeline-dedup.md}.
     */
    public void replay(Collection<String> ids) {
        actOnMatching(ids, this::replayBatch);
    }

    /** Delete each selected message from the DLQ; ids not found are logged and left for a retry. */
    public void delete(Collection<String> ids) {
        actOnMatching(ids, matched -> deleteBatch(requireDlqUrl(), matched));
    }

    private void actOnMatching(Collection<String> requestedIds, Consumer<List<Message>> batchAction) {
        String dlqUrl = requireDlqUrl();
        Set<String> remaining = new LinkedHashSet<>(requestedIds);
        int processed = 0;
        for (int poll = 0; poll < MAX_RECEIVE_POLLS && !remaining.isEmpty(); poll++) {
            List<Message> batch = receive(dlqUrl, MAX_RECEIVE, 0);
            if (batch.isEmpty()) {
                break;
            }
            List<Message> matched = new ArrayList<>();
            List<Message> unrequested = new ArrayList<>();
            for (Message message : batch) {
                if (remaining.remove(idOf(message))) {
                    matched.add(message);
                } else {
                    // Received (now in-flight) but not requested — release it back immediately.
                    unrequested.add(message);
                }
            }
            release(unrequested);
            if (!matched.isEmpty()) {
                batchAction.accept(matched);
                processed += matched.size();
            }
        }
        log.info("DLQ batch actioned {} message(s), {} not found", processed, remaining.size());
    }

    private List<Message> receive(String dlqUrl, int maxMessages, int waitSeconds) {
        return sqsAsyncClient.receiveMessage(request -> request
                .queueUrl(dlqUrl)
                .maxNumberOfMessages(maxMessages)
                .waitTimeSeconds(waitSeconds)
                .visibilityTimeout(SCAN_VISIBILITY_SECONDS)
                .messageSystemAttributeNames(
                    MessageSystemAttributeName.MESSAGE_GROUP_ID,
                    MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID,
                    MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
            .join()
            .messages();
    }

    /**
     * Re-send a batch (≤10) to the source queue, then delete from the DLQ only the entries that
     * re-sent. Batch entry ids are the index into {@code messages}, so the per-entry results map back.
     *
     * <p>Each entry gets a fresh, unique {@link #replayDedupId(Message) transport dedup id} rather than
     * reusing the message's original {@code idOf(message)} — reusing it would collide with the source
     * queue's 5-minute FIFO dedup window when a message is replayed soon after its original send (a
     * fast-failing message can reach the DLQ in well under 5 minutes), causing SQS to silently accept
     * the send without delivering it, after which this method would delete the "replayed" message from
     * the DLQ — a silent, unrecoverable loss. See {@code docs/notification-pipeline-dedup.md}.
     *
     * <p>The ASB-level dedup key stays stable across the replay regardless: {@link NotificationSqsListener}
     * derives the ASB messageId from the body {@code eventId}, not from this transport dedup id.
     */
    private void replayBatch(List<Message> messages) {
        List<SendMessageBatchRequestEntry> sendEntries = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            sendEntries.add(SendMessageBatchRequestEntry.builder()
                .id(String.valueOf(i))
                .messageBody(message.body())
                .messageGroupId(message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID))
                .messageDeduplicationId(replayDedupId(message))
                .build());
        }
        SendMessageBatchResponse sent = sqsAsyncClient.sendMessageBatch(request -> request
                .queueUrl(sqsConfig.queueUrl())
                .entries(sendEntries))
            .join();
        if (!sent.failed().isEmpty()) {
            log.warn("Replay: {} of {} message(s) failed to re-send and remain on the DLQ",
                sent.failed().size(), messages.size());
        }
        List<Message> reSent = sent.successful().stream()
            .map(entry -> messages.get(Integer.parseInt(entry.id())))
            .toList();
        if (!reSent.isEmpty()) {
            deleteBatch(requireDlqUrl(), reSent);
        }
        log.info("Replayed {} DLQ message(s) to the source queue", reSent.size());
    }

    private void deleteBatch(String queueUrl, List<Message> messages) {
        List<DeleteMessageBatchRequestEntry> entries = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            entries.add(DeleteMessageBatchRequestEntry.builder()
                .id(String.valueOf(i))
                .receiptHandle(messages.get(i).receiptHandle())
                .build());
        }
        DeleteMessageBatchResponse response = sqsAsyncClient.deleteMessageBatch(request -> request
                .queueUrl(queueUrl)
                .entries(entries))
            .join();
        if (!response.failed().isEmpty()) {
            log.warn("Delete: {} of {} message(s) could not be removed from the DLQ",
                response.failed().size(), messages.size());
        }
    }

    /**
     * Make every scanned-but-not-actioned message visible again so a following request can see it.
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
                        .queueUrl(requireDlqUrl())
                        .entries(entries))
                    .join();
            } catch (RuntimeException e) {
                log.debug("Could not release a browsed DLQ batch early: {}", e.getMessage());
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
     * The stable id a caller selects on: the {@code eventId} from the body, otherwise the SQS
     * {@code MessageDeduplicationId}. This is the operator-facing id ({@link DlqMessage#id()}) and the
     * ASB {@code messageId} ({@link NotificationSqsListener} derives it the same way) — but it is
     * deliberately NOT reused as the source-queue transport dedup id on replay; see
     * {@link #replayDedupId(Message)}.
     */
    private String idOf(Message message) {
        return EventEnvelope.eventId(objectMapper, message.body())
            .orElseGet(() -> message.attributes().get(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID));
    }

    /**
     * A fresh, unique {@code MessageDeduplicationId} for re-sending {@code message} to the source queue
     * on replay — never the same value twice, so the source queue's 5-minute FIFO dedup window can never
     * suppress a deliberate re-drive (see {@link #replayBatch}). Prefixed with {@link #idOf(Message)} so
     * the transport id stays traceable back to the operator-facing selection id in SQS-level logs/traces,
     * while staying comfortably under SQS's 128-character {@code MessageDeduplicationId} limit.
     */
    private String replayDedupId(Message message) {
        return idOf(message) + ":replay:" + UUID.randomUUID();
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

    private String requireDlqUrl() {
        String dlqUrl = sqsConfig.dlqUrl();
        if (dlqUrl == null || dlqUrl.isBlank()) {
            throw new IllegalStateException(
                "DLQ URL is not configured (set NOTIFICATION_SQS_DLQ_URL / aws.sqs.notification.dlq-url)");
        }
        return dlqUrl;
    }
}
