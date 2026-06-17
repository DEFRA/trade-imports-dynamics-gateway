package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.azure.messaging.servicebus.ServiceBusException;
import io.awspring.cloud.sqs.listener.errorhandler.ErrorHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;

/**
 * Classifies exceptions from the SQS notification listener as retryable or non-retryable.
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

        if (cause instanceof ServiceBusException sbEx && sbEx.isTransient()) {
            retryCounter.increment();
            log.warn("Transient ASB error, message left for retry: {}", sbEx.getMessage());
            throw new RuntimeException(t);
        }

        if (cause instanceof ServiceBusException sbEx) {
            discardedCounter.increment();
            log.error("Non-transient ASB error, message will be deleted: {}", sbEx.getMessage(), sbEx);
            return;
        }

        if (cause instanceof IllegalStateException) {
            retryCounter.increment();
            log.warn("Sender in illegal state, message left for retry: {}", cause.getMessage());
            throw new RuntimeException(t);
        }

        // NullPointerException, other unexpected errors — non-retryable
        discardedCounter.increment();
        log.error("Non-retryable error, message will be deleted: {}", cause.getMessage(), cause);
    }

    private Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (current instanceof ServiceBusException) {
                return current;
            }
        }
        // No ServiceBusException found — return the immediate cause (or the original if no cause)
        return t.getCause() != null ? t.getCause() : t;
    }
}
