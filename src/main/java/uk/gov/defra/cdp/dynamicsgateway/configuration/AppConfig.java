package uk.gov.defra.cdp.dynamicsgateway.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppConfig(String baseUrl) {}
