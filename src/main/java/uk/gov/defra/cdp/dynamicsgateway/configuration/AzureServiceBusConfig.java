package uk.gov.defra.cdp.dynamicsgateway.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "azure.servicebus")
public record AzureServiceBusConfig(
    @NotBlank String connectionString,
    @NotBlank String queue
) {}
