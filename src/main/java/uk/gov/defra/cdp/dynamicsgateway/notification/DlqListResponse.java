package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Response for a DLQ list request. Serialised snake_case per the REST API guidelines; the top-level
 * payload is an object (never a bare array).
 *
 * @param messages          the page of messages received (≤ requested limit, ≤ 10 per SQS call)
 * @param approximateCount  the queue's {@code ApproximateNumberOfMessages} — eventually consistent,
 *                          a snapshot that can drift between calls (SQS has no stable count/cursor)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DlqListResponse(List<DlqMessage> messages, long approximateCount) {
}
