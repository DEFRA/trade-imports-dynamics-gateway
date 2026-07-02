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
     * Fraction of {@code visibilityTimeoutSeconds} the jittered retry window must stay under. Covers
     * what {@link Retry#worstCaseJitteredWindowMillis()} still doesn't model: per-attempt ASB call
     * duration. {@code ServiceBusSenderClient} (see {@code AzureServiceBusClientConfig}) is built with
     * no explicit {@code retryOptions}, so it runs on the Azure SDK's own default retry/timeout policy —
     * and {@link uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender} classifies an ASB timeout
     * as retryable, so a single in-process attempt's real elapsed time is "backoff sleep + however long
     * the SDK's own internal retry/timeout takes", which this app does not bound today. 20% margin is a
     * pragmatic cushion for that unmodelled cost, not a precise bound.
     */
    private static final double RETRY_WINDOW_SAFETY_MARGIN = 0.8;

    /**
     * Fail fast if the in-process retry window could outlive the SQS visibility timeout. While the
     * {@code RetryTemplate} retries, the message stays invisible but is NOT extended; once the
     * visibility timeout expires it reappears and a second consumer processes it concurrently,
     * causing a duplicate ASB publish.
     *
     * <p>Checks the <strong>jittered</strong> worst case ({@link Retry#worstCaseJitteredWindowMillis()})
     * against {@link #RETRY_WINDOW_SAFETY_MARGIN} of the visibility timeout — not the deterministic
     * {@link Retry#worstCaseWindowMillis()} — because {@code AwsConfig.notificationRetryTemplate} builds
     * its {@code RetryTemplate} with {@code withRandom = true} (jittered backoff), so the deterministic
     * figure understates the real worst case. The shipped defaults (1s/2s/4s deterministic → 14s
     * jittered, vs 30s × 0.8 = 24s threshold) are safe, but the intervals/attempts/multiplier are
     * env-overridable, so enforce the invariant rather than trusting configuration.
     */
    @AssertTrue(message = "aws.sqs.notification.retry window (jittered worst case across max-attempts, "
        + "with a 20% safety margin) must be shorter than visibility-timeout-seconds, otherwise an "
        + "in-process retry can outlive the SQS visibility timeout and the message is processed "
        + "concurrently")
    public boolean isRetryWindowWithinVisibilityTimeout() {
        // @NotNull reports a null retry block; nothing to cross-check until it is present.
        return retry == null
            || retry.worstCaseJitteredWindowMillis() < visibilityTimeoutSeconds * 1000L * RETRY_WINDOW_SAFETY_MARGIN;
    }

    /**
     * In-process exponential backoff applied to a transient ASB publish failure before the message
     * is released back to SQS. Drives a Spring {@code RetryTemplate} + {@code ExponentialBackOffPolicy}
     * that retries only {@link uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsRetryableException}.
     *
     * <p>The whole retry window (sum of the backoff intervals across {@code maxAttempts}, under the
     * jittered policy actually built — see {@link #worstCaseJitteredWindowMillis()}) must stay
     * comfortably below {@code visibilityTimeoutSeconds}; otherwise the message reappears on the queue
     * and is processed concurrently. Longer outages fall through to SQS redelivery and, after
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
         * Worst-case total time spent sleeping between attempts under Spring's deterministic
         * {@code ExponentialBackOffPolicy}: {@code maxAttempts - 1} sleeps starting at
         * {@code initialInterval} and growing by {@code multiplier}, each capped at
         * {@code maxInterval}. Useful for logging/documentation, but NOT what
         * {@link NotificationSqsConfig#isRetryWindowWithinVisibilityTimeout()} checks — the actual
         * {@code RetryTemplate} is jittered; see {@link #worstCaseJitteredWindowMillis()}.
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

        /**
         * Worst-case total time spent sleeping between attempts under Spring's
         * {@code ExponentialRandomBackOffPolicy} (the policy {@code AwsConfig.notificationRetryTemplate}
         * actually builds, {@code withRandom = true}). Its per-step jitter factor is
         * {@code 1 + rand·(multiplier - 1)} with {@code rand ∈ [0,1)}, applied to the deterministic
         * step and re-capped at {@code maxInterval} — so each step's supremum (never quite reached,
         * but approached arbitrarily closely) is {@code min(deterministicStep * multiplier, maxInterval)}.
         * Using that supremum per step is a deliberately conservative (safe) upper bound for a
         * fail-fast guard: it may reject a config that would, in practice, always land marginally under
         * the limit, but it will never accept one that can exceed it.
         */
        long worstCaseJitteredWindowMillis() {
            long total = 0;
            double interval = initialInterval;
            for (int sleep = 1; sleep < maxAttempts; sleep++) {
                double deterministicStep = Math.min(interval, maxInterval);
                total += (long) Math.min(deterministicStep * multiplier, maxInterval);
                interval *= multiplier;
            }
            return total;
        }
    }
}
