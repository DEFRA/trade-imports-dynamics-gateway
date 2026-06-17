package uk.gov.defra.cdp.dynamicsgateway.notification;

import io.awspring.cloud.sqs.listener.errorhandler.ErrorHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsRetryableException;

/**
 * Routes SQS listener errors based on exception type — no unwrapping needed because
 * {@link uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender} already classifies
 * ASB failures into {@link SqsRetryableException} or
 * {@link uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException}.
 *
 * <p>Spring Cloud AWS behaviour:
 * <ul>
 *   <li><strong>Normal return</strong> from this handler — message is acknowledged (deleted from SQS).
 *       Used for non-retryable errors where retrying the same message will never succeed.</li>
 *   <li><strong>Thrown exception</strong> from this handler — message is NOT acknowledged. After the
 *       visibility timeout expires, the message becomes available for retry, eventually routing to
 *       the DLQ after {@code maxReceiveCount}.</li>
 * </ul>
 */
@Slf4j
public class NotificationErrorHandler implements ErrorHandler<Object> {

    private final Counter retryCounter;
    private final Counter discardedCounter;

    public NotificationErrorHandler(MeterRegistry meterRegistry) {
        this.retryCounter = Counter.builder("notification.sqs.errors")
            .tag("action", "retry")
            .description("Errors left for SQS retry (transient)")
            .register(meterRegistry);
        this.discardedCounter = Counter.builder("notification.sqs.errors")
            .tag("action", "discarded")
            .description("Errors discarded as non-retryable (message deleted)")
            .register(meterRegistry);
    }

    @Override
    public void handle(Message<Object> message, Throwable t) {
        Throwable cause = unwrap(t);
        if (cause instanceof SqsRetryableException retryableException) {
            retryCounter.increment();
            log.warn("Retryable error, message left for retry: {}", cause.getMessage());
            throw retryableException;
        }

        discardedCounter.increment();
        log.error("Non-retryable error, message will be deleted: {}", cause.getMessage(), cause);
    }

    private Throwable unwrap(Throwable t) {
        return t.getCause() != null && !(t instanceof SqsRetryableException) ? t.getCause() : t;
    }
}
