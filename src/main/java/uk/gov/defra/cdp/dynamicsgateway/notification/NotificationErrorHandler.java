package uk.gov.defra.cdp.dynamicsgateway.notification;

import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.errorhandler.ErrorHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsRetryableException;

/**
 * Rethrows a failure whose cause chain holds an {@link SqsRetryableException}, so SQS redelivers the
 * message, and returns normally for any other failure, so the message is deleted.
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
        Throwable classified = findClassifiedCause(t);
        if (classified instanceof SqsRetryableException retryableException) {
            retryCounter.increment();
            log.warn("Retryable error, message for aggregate {} left for retry", aggregateIdOf(message), classified);
            throw retryableException;
        }

        discardedCounter.increment();
        log.error("Non-retryable error, message for aggregate {} will be deleted", aggregateIdOf(message), t);
    }

    private Throwable findClassifiedCause(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof SqsRetryableException || current instanceof SqsNonRetryableException) {
                return current;
            }
            current = current.getCause();
        }
        return t;
    }

    private static Object aggregateIdOf(Message<Object> message) {
        return message.getHeaders().get(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER);
    }
}
