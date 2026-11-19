package uk.gov.defra.cdp.dynamicsgateway.configuration;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.cloudwatchlogs.emf.config.EnvironmentConfigurationProvider;
import software.amazon.cloudwatchlogs.emf.environment.Environments;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import uk.gov.defra.cdp.dynamicsgateway.metrics.EmfMeterRegistry;

/**
 * The meter registry the application records into: a composite of a cumulative registry, which the
 * actuator's {@code /metrics} endpoint reads, and the registry that publishes to CloudWatch when
 * {@code aws.emf.enabled} is true. Also a startup log line saying publishing is off when it is not,
 * and the aspects that record {@code @Timed} and {@code @Counted} methods.
 */
@Slf4j
@Configuration
public class MetricsConfig {

  @Bean
  SimpleMeterRegistry cumulativeMeterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  @Primary
  CompositeMeterRegistry meterRegistry(
      SimpleMeterRegistry cumulativeMeterRegistry,
      ObjectProvider<EmfMeterRegistry> emfMeterRegistry) {
    CompositeMeterRegistry composite = new CompositeMeterRegistry();
    composite.add(cumulativeMeterRegistry);
    emfMeterRegistry.ifAvailable(composite::add);
    return composite;
  }

  @Bean
  MetricsEndpoint metricsEndpoint(SimpleMeterRegistry cumulativeMeterRegistry) {
    return new MetricsEndpoint(cumulativeMeterRegistry);
  }

  @Bean
  @ConditionalOnProperty(name = "aws.emf.enabled", havingValue = "true")
  EmfMeterRegistry emfMeterRegistry(
      @Value("${aws.emf.namespace}") String namespace,
      @Value("${aws.emf.step}") Duration step,
      @Value("${aws.emf.environment:}") String environment) {
    if (!environment.isBlank()) {
      EnvironmentConfigurationProvider.getConfig()
          .setEnvironmentOverride(Environments.valueOf(environment));
      log.info("Writing metrics to the {} EMF environment", environment);
    }
    EmfMeterRegistry emfRegistry =
        new EmfMeterRegistry(namespace, step, Clock.SYSTEM, MetricsLogger::new);
    emfRegistry.start(new NamedThreadFactory("emf-meter-registry"));
    log.info("Publishing metrics to CloudWatch namespace {} every {}", namespace, step);
    return emfRegistry;
  }

  @Bean
  @ConditionalOnProperty(name = "aws.emf.enabled", havingValue = "false", matchIfMissing = true)
  ApplicationRunner emfPublishingOffLog() {
    return args -> log.info("Not publishing metrics to CloudWatch: aws.emf.enabled is not true");
  }

  @Bean
  TimedAspect timedAspect(MeterRegistry meterRegistry) {
    log.debug("Creating TimedAspect for {}", meterRegistry.getClass().getSimpleName());
    return new TimedAspect(meterRegistry);
  }

  @Bean
  CountedAspect countedAspect(MeterRegistry meterRegistry) {
    return new CountedAspect(meterRegistry);
  }
}
