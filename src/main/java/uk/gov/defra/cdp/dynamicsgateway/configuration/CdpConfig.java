package uk.gov.defra.cdp.dynamicsgateway.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cdp")
public record CdpConfig(
    String certificate,
    MetricsConfig metrics,
    String serviceVersion,
    TracingConfig tracing,
    CloudwatchConfig cloudwatch,
    String proxyUrl) {

  public record MetricsConfig(boolean enabled) {}

  public record TracingConfig(String headerName) {}

  public record CloudwatchConfig(String endpoint) {}
}
