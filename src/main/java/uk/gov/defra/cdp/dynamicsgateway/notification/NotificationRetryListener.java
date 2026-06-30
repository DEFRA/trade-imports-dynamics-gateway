package uk.gov.defra.cdp.dynamicsgateway.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

/**
 * Logs in-process retry activity for transient ASB publish failures, so operators can distinguish
 * an in-process {@code RetryTemplate} retry from an independent SQS redelivery. Attached to the
 * {@code notificationRetryTemplate} in {@code AwsConfig}.
 */
@Slf4j
public class NotificationRetryListener implements RetryListener {

    private final int maxAttempts;

    public NotificationRetryListener(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    @Override
    public <T, E extends Throwable> void onError(
            RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        log.warn("Transient ASB publish failed (attempt {} of {}): {}",
            context.getRetryCount(), maxAttempts, throwable.getMessage());
    }

    @Override
    public <T, E extends Throwable> void close(
            RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        if (throwable != null) {
            log.error("Transient ASB publish retries exhausted after {} attempt(s); "
                + "leaving message in SQS for redelivery", context.getRetryCount(), throwable);
        }
    }
}
