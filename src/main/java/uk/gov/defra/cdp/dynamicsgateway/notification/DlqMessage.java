package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * A single DLQ message projected for the admin list/detail view. Serialised snake_case per the REST
 * API guidelines.
 *
 * @param id                       stable identifier used to select for replay/delete — the
 *                                 {@code eventId} from the body when present (EUDPA-261), otherwise
 *                                 the SQS {@code MessageDeduplicationId}
 * @param messageGroupId           the FIFO group id (the notification aggregateId)
 * @param deduplicationId          the SQS {@code MessageDeduplicationId} carried on the DLQ message
 * @param approximateReceiveCount  how many times the message was received before being dead-lettered
 * @param body                     the raw message body
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DlqMessage(
    String id,
    String messageGroupId,
    String deduplicationId,
    int approximateReceiveCount,
    String body) {
}
