package uk.gov.defra.cdp.dynamicsgateway.exceptions;

/**
 * Signals a permanent failure when forwarding an SQS message to Azure Service Bus.
 *
 * <p>Thrown by {@link uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender} for non-transient
 * ASB errors (entity not found, unauthorized, message too large) and unexpected exceptions.
 * {@link uk.gov.defra.cdp.dynamicsgateway.notification.NotificationErrorHandler} treats this as
 * non-retryable: the SQS message is acknowledged (deleted) so it does not block the FIFO queue.
 * {@link GlobalExceptionHandler} maps it to HTTP 502.
 */
public class SqsNonRetryableException extends RuntimeException {

    public SqsNonRetryableException(String message) {
        super(message);
    }

    public SqsNonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
