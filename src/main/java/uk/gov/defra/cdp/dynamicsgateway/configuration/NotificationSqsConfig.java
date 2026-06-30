package uk.gov.defra.cdp.dynamicsgateway.configuration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aws.sqs.notification")
public record NotificationSqsConfig(
    @NotBlank String queueUrl,
    // DLQ URL for the list/replay/delete API. Not @NotBlank: the listener pipeline works without it,
    // and DlqService fails fast with a clear message if an operation is attempted while it is unset.
    String dlqUrl,
    @Min(1) @Max(43200) int visibilityTimeoutSeconds,
    @Min(0) @Max(20) int waitTimeSeconds,
    @Min(1) @Max(10) int maxMessages,
    @NotNull Retry retry) {

    /**
     * Fail fast if the in-process retry window could outlive the SQS visibility timeout. While the
     * {@code RetryTemplate} retries, the message stays invisible but is NOT extended; once the
     * visibility timeout expires it reappears and a second consumer processes it concurrently,
     * causing a duplicate ASB publish. The defaults (1s+2s+4s = 7s across 4 attempts, vs 30s
     * visibility) are safe, but the intervals/attempts are env-overridable, so enforce the
     * invariant the {@link Retry} Javadoc documents rather than trusting configuration.
     */
    @AssertTrue(message = "aws.sqs.notification.retry window (sum of backoff intervals across "
        + "max-attempts) must be shorter than visibility-timeout-seconds, otherwise an in-process "
        + "retry outlives the SQS visibility timeout and the message is processed concurrently")
    public boolean isRetryWindowWithinVisibilityTimeout() {
        // @NotNull reports a null retry block; nothing to cross-check until it is present.
        return retry == null || retry.worstCaseWindowMillis() < visibilityTimeoutSeconds * 1000L;
    }

    /**
     * In-process exponential backoff applied to a transient ASB publish failure before the message
     * is released back to SQS. Drives a Spring {@code RetryTemplate} + {@code ExponentialBackOffPolicy}
     * that retries only {@link uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsRetryableException}.
     *
     * <p>The whole retry window (sum of the backoff intervals across {@code maxAttempts}) must stay
     * below {@code visibilityTimeoutSeconds}; otherwise the message reappears on the queue and is
     * processed concurrently. Longer outages fall through to SQS redelivery and, after
     * {@code maxReceiveCount}, the DLQ.
     *
     * @param maxAttempts     total attempts including the first (1 = no retry)
     * @param initialInterval first backoff (ms)
     * @param multiplier      growth factor between attempts
     * @param maxInterval     ceiling for any single backoff (ms)
     */
    public record Retry(
        @Min(1) @Max(10) int maxAttempts,
        @Min(1) long initialInterval,
        @DecimalMin("1.0") double multiplier,
        @Min(1) long maxInterval) {

        /**
         * Worst-case total time spent sleeping between attempts, mirroring Spring's
         * {@code ExponentialBackOffPolicy}: {@code maxAttempts - 1} sleeps starting at
         * {@code initialInterval} and growing by {@code multiplier}, each capped at
         * {@code maxInterval}.
         */
        long worstCaseWindowMillis() {
            long total = 0;
            double interval = initialInterval;
            for (int sleep = 1; sleep < maxAttempts; sleep++) {
                total += (long) Math.min(interval, maxInterval);
                interval *= multiplier;
            }
            return total;
        }
    }
}
