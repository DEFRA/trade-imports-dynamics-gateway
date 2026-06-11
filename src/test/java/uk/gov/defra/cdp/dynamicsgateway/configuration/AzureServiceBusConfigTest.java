package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.amqp.AmqpTransportType;
import org.junit.jupiter.api.Test;

class AzureServiceBusConfigTest {

    @Test
    void toString_shouldRedactConnectionStringAndIncludeTransportType() {
        // Given
        AzureServiceBusConfig config = new AzureServiceBusConfig(
            "Endpoint=sb://example.servicebus.windows.net/;SharedAccessKey=secret",
            AmqpTransportType.AMQP_WEB_SOCKETS);

        // When
        String result = config.toString();

        // Then
        assertThat(result).contains("***").doesNotContain("secret");
        assertThat(result).contains("AMQP_WEB_SOCKETS");
    }
}
