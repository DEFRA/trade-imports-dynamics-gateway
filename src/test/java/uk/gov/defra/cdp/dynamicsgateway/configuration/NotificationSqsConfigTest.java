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
        // Given — production defaults: 4 attempts → 3 sleeps of 1s, 2s, 4s.
        Retry retry = new Retry(4, 1000, 2.0, 10000);

        // When / Then
        assertThat(retry.worstCaseWindowMillis()).isEqualTo(7000L);
    }

    @Test
    void worstCaseWindowMillis_shouldCapEachSleepAtMaxInterval() {
        // Given — 5 attempts → sleeps 1s, 2s, 4s, 8s but capped at 3s each = 1000 + 2000 + 3000 + 3000.
        Retry retry = new Retry(5, 1000, 2.0, 3000);

        // When / Then
        assertThat(retry.worstCaseWindowMillis()).isEqualTo(9000L);
    }

    @Test
    void worstCaseWindowMillis_shouldBeZero_whenNoRetries() {
        // Given — a single attempt means no retry sleeps.
        Retry retry = new Retry(1, 1000, 2.0, 10000);

        // When / Then
        assertThat(retry.worstCaseWindowMillis()).isZero();
    }

    @Test
    void worstCaseJitteredWindowMillis_shouldApplyMultiplierAsJitterSupremumPerStep() {
        // Given — production defaults: deterministic 1s/2s/4s → jittered supremum 2s/4s/8s = 14s.
        Retry retry = new Retry(4, 1000, 2.0, 10000);

        // When / Then
        assertThat(retry.worstCaseJitteredWindowMillis()).isEqualTo(14000L);
    }

    @Test
    void worstCaseJitteredWindowMillis_shouldCapEachJitteredStepAtMaxInterval() {
        // Given — deterministic steps 1s/2s/3s(capped)/3s(capped); jittered steps
        // min(1s*2,3s)=2s, min(2s*2,3s)=3s(capped), min(3s*2,3s)=3s(capped), 3s(capped) = 11s.
        Retry retry = new Retry(5, 1000, 2.0, 3000);

        // When / Then
        assertThat(retry.worstCaseJitteredWindowMillis()).isEqualTo(11000L);
    }

    @Test
    void worstCaseJitteredWindowMillis_shouldEqualDeterministic_whenMultiplierIsOne() {
        // Given — multiplier 1.0 is the minimum allowed (@DecimalMin("1.0")); the jitter factor
        // 1 + rand*(multiplier-1) collapses to exactly 1 regardless of rand, so jitter has no effect.
        Retry retry = new Retry(4, 2000, 1.0, 10000);

        // When / Then
        assertThat(retry.worstCaseJitteredWindowMillis()).isEqualTo(retry.worstCaseWindowMillis());
    }

    @Test
    void isRetryWindowWithinVisibilityTimeout_shouldPass_whenWindowShorterThanVisibility() {
        // Given / When / Then — 14s jittered window vs 30s * 0.8 = 24s margin threshold.
        assertThat(config(30, new Retry(4, 1000, 2.0, 10000)).isRetryWindowWithinVisibilityTimeout())
            .isTrue();
    }

    @Test
    void isRetryWindowWithinVisibilityTimeout_shouldFail_whenWindowExceedsVisibility() {
        // Given / When / Then — 8 attempts ≈ 45s deterministic / 54s jittered window vs 30s visibility.
        assertThat(config(30, new Retry(8, 1000, 2.0, 10000)).isRetryWindowWithinVisibilityTimeout())
            .isFalse();
    }

    @Test
    void isRetryWindowWithinVisibilityTimeout_shouldFail_whenWindowEqualsVisibility() {
        // Given / When / Then — boundary: 2s window vs 2s visibility must fail (retry must finish before expiry).
        assertThat(config(2, new Retry(3, 1000, 1.0, 5000)).isRetryWindowWithinVisibilityTimeout())
            .isFalse();
    }

    @Test
    void isRetryWindowWithinVisibilityTimeout_shouldFail_onceJitterIsModelled_evenThoughDeterministicWindowPasses() {
        // Given — the regression the old, deterministic-only check missed: deterministic window
        // (6s + 12s = 18s) is comfortably under the 19s visibility timeout, so the pre-fix check would
        // have passed this config at startup. But the actual (jittered) RetryTemplate's worst case
        // (min(6s*2,20s)=12s + min(12s*2,20s)=20s(capped) = 32s) is well over both the raw 19s visibility
        // and the 19s * 0.8 = 15.2s margin threshold.
        Retry retry = new Retry(3, 6000, 2.0, 20000);
        assertThat(retry.worstCaseWindowMillis())
            .as("sanity check: this config would have passed the old deterministic-only guard")
            .isLessThan(19_000L);

        assertThat(config(19, retry).isRetryWindowWithinVisibilityTimeout()).isFalse();
    }

    @Test
    void isRetryWindowWithinVisibilityTimeout_shouldFail_whenJitteredWindowPassesRawVisibilityButNotTheSafetyMargin() {
        // Given — isolates the safety-margin's own effect (multiplier 1.0, so jittered == deterministic:
        // no jitter involved). Window is 8s: under the raw 9s visibility, but at/over the 9s * 0.8 = 7.2s
        // margin threshold meant to absorb unmodelled per-attempt ASB call duration.
        Retry retry = new Retry(3, 4000, 1.0, 10000);
        assertThat(retry.worstCaseJitteredWindowMillis()).isEqualTo(8000L);

        assertThat(config(9, retry).isRetryWindowWithinVisibilityTimeout()).isFalse();
    }

    @Test
    void isRetryWindowWithinVisibilityTimeout_shouldPass_whenRetryBlockIsNull() {
        // Given / When / Then — @NotNull reports the null block; the cross-field check must not throw.
        assertThat(config(30, null).isRetryWindowWithinVisibilityTimeout()).isTrue();
    }
}
