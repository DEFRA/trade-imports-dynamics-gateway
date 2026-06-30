package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig.Retry;

class NotificationSqsConfigTest {

    private static NotificationSqsConfig config(int visibilityTimeoutSeconds, Retry retry) {
        return new NotificationSqsConfig("http://queue", "http://dlq", visibilityTimeoutSeconds, 20, 10, retry);
    }

    @Test
    void worstCaseWindowMillis_shouldSumExponentialBackoffsAcrossAttempts() {
        // Production defaults: 4 attempts → 3 sleeps of 1s, 2s, 4s.
        Retry retry = new Retry(4, 1000, 2.0, 10000);

        assertThat(retry.worstCaseWindowMillis()).isEqualTo(7000L);
    }

    @Test
    void worstCaseWindowMillis_shouldCapEachSleepAtMaxInterval() {
        // 5 attempts → sleeps 1s, 2s, 4s, 8s but capped at 3s each = 1000 + 2000 + 3000 + 3000.
        Retry retry = new Retry(5, 1000, 2.0, 3000);

        assertThat(retry.worstCaseWindowMillis()).isEqualTo(9000L);
    }

    @Test
    void worstCaseWindowMillis_shouldBeZero_whenNoRetries() {
        Retry retry = new Retry(1, 1000, 2.0, 10000);

        assertThat(retry.worstCaseWindowMillis()).isZero();
    }

    @Test
    void isRetryWindowWithinVisibilityTimeout_shouldPass_whenWindowShorterThanVisibility() {
        // 7s window vs 30s visibility.
        assertThat(config(30, new Retry(4, 1000, 2.0, 10000)).isRetryWindowWithinVisibilityTimeout())
            .isTrue();
    }

    @Test
    void isRetryWindowWithinVisibilityTimeout_shouldFail_whenWindowExceedsVisibility() {
        // 8 attempts ≈ 45s window vs 30s visibility.
        assertThat(config(30, new Retry(8, 1000, 2.0, 10000)).isRetryWindowWithinVisibilityTimeout())
            .isFalse();
    }

    @Test
    void isRetryWindowWithinVisibilityTimeout_shouldFail_whenWindowEqualsVisibility() {
        // Boundary: 2s window vs 2s visibility must fail — the retry must finish strictly before expiry.
        assertThat(config(2, new Retry(3, 1000, 1.0, 5000)).isRetryWindowWithinVisibilityTimeout())
            .isFalse();
    }

    @Test
    void isRetryWindowWithinVisibilityTimeout_shouldPass_whenRetryBlockIsNull() {
        // @NotNull reports the null block; the cross-field check must not throw.
        assertThat(config(30, null).isRetryWindowWithinVisibilityTimeout()).isTrue();
    }
}
