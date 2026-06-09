package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.defra.cdp.dynamicsgateway.service.EmfMetricsPublisher;

class MetricsConfigurationPropertiesTest {

    @Test
    void emfMetricsPublisher_shouldHaveConditionalOnPropertyAnnotation() {
        ConditionalOnProperty annotation = EmfMetricsPublisher.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).containsExactly("management.metrics.enabled");
        assertThat(annotation.havingValue()).isEqualTo("true");
    }

    @Test
    void emfMetricsPublisher_shouldBeAnnotatedAsService() {
        Service annotation = EmfMetricsPublisher.class.getAnnotation(Service.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    void publishMetrics_shouldBeScheduled() throws NoSuchMethodException {
        Method method = EmfMetricsPublisher.class.getMethod("publishMetrics");
        Scheduled annotation = method.getAnnotation(Scheduled.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.fixedRate()).isEqualTo(60000);
    }
}
