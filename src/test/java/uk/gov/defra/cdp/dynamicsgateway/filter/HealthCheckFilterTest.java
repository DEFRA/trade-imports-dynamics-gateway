package uk.gov.defra.cdp.dynamicsgateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthCheckFilterTest {

    @Mock
    private ILoggingEvent event;

    private final HealthCheckFilter filter = new HealthCheckFilter();

    @Test
    void decide_shouldReturnNeutral_whenMdcPropertyMapIsNull() {
        when(event.getMDCPropertyMap()).thenReturn(null);

        assertThat(filter.decide(event)).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void decide_shouldReturnNeutral_whenUrlIsAbsentFromMdc() {
        when(event.getMDCPropertyMap()).thenReturn(Map.of());

        assertThat(filter.decide(event)).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void decide_shouldReturnDeny_whenUrlContainsHealth() {
        when(event.getMDCPropertyMap()).thenReturn(Map.of("url.full", "http://localhost:8080/health"));

        assertThat(filter.decide(event)).isEqualTo(FilterReply.DENY);
    }

    @Test
    void decide_shouldReturnNeutral_whenUrlDoesNotContainHealth() {
        when(event.getMDCPropertyMap()).thenReturn(Map.of("url.full", "http://localhost:8080/api/notifications"));

        assertThat(filter.decide(event)).isEqualTo(FilterReply.NEUTRAL);
    }
}
