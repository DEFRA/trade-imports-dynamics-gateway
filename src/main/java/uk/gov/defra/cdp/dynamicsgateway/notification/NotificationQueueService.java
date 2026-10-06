package uk.gov.defra.cdp.dynamicsgateway.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;

/**
 * Reads the backlog of the notification source queue, so load runs can watch it drain.
 *
 * <p>{@code GetQueueAttributes} counts are eventually consistent, so a reading is a snapshot that can
 * drift between calls. A failed SQS call surfaces as a {@link java.util.concurrent.CompletionException},
 * mapped to a 502 by {@link uk.gov.defra.cdp.dynamicsgateway.exceptions.GlobalExceptionHandler}.
 */
@Service
@RequiredArgsConstructor
public class NotificationQueueService {

    private final SqsAsyncClient sqsAsyncClient;
    private final NotificationSqsConfig sqsConfig;

    /** The source queue's approximate visible and in-flight message counts. */
    public NotificationQueueDepth depth() {
        var attributes = sqsAsyncClient.getQueueAttributes(request -> request
                .queueUrl(sqsConfig.queueUrl())
                .attributeNames(
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE))
            .join()
            .attributes();
        return new NotificationQueueDepth(
            count(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)),
            count(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)));
    }

    private long count(String value) {
        return value == null ? 0L : Long.parseLong(value);
    }
}
