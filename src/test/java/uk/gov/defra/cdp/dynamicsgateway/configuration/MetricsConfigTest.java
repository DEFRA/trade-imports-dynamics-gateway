package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsConfigTest {

    private MetricsConfig metricsConfig;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        metricsConfig = new MetricsConfig();
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void timedAspect_shouldCreateTimedAspectWithRegistry() {
        TimedAspect result = metricsConfig.timedAspect(meterRegistry);

        assertThat(result).isNotNull().isInstanceOf(TimedAspect.class);
    }

    @Test
    void countedAspect_shouldCreateCountedAspectWithRegistry() {
        CountedAspect result = metricsConfig.countedAspect(meterRegistry);

        assertThat(result).isNotNull().isInstanceOf(CountedAspect.class);
    }
}
