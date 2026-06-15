package uk.gov.defra.cdp.dynamicsgateway.configuration;

import com.azure.core.amqp.AmqpTransportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "azure.servicebus")
public record AzureServiceBusConfig(
    @NotBlank String connectionString,
    @NotNull AmqpTransportType transportType
) {
  @Override
  public String toString() {
    return "AzureServiceBusConfig[connectionString=***, transportType=" + transportType + "]";
  }
}
