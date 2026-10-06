package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Backlog of the notification source queue. Serialised snake_case per the REST API guidelines; the
 * payload is an object.
 *
 * @param approximateCount         the queue's {@code ApproximateNumberOfMessages} — messages waiting to
 *                                 be received; eventually consistent
 * @param approximateInFlightCount the queue's {@code ApproximateNumberOfMessagesNotVisible} — messages
 *                                 received but not yet deleted; eventually consistent
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NotificationQueueDepth(long approximateCount, long approximateInFlightCount) {
}
