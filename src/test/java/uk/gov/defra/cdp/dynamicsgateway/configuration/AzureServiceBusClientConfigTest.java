package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.amqp.AmqpTransportType;
import com.azure.core.amqp.ProxyOptions;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureServiceBusClientConfigTest {

    private final AzureServiceBusConfig asbConfig = new AzureServiceBusConfig(
        "Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=dGVzdA==",
        AmqpTransportType.AMQP_WEB_SOCKETS);

    @Test
    void serviceBusSenderClient_shouldApplyProxyOptions_whenProxyUrlIsConfigured() {
        // Given
        CdpConfig cdpConfig = new CdpConfig(null, null, null, null, "http://localhost:3128");

        try (var mocked = Mockito.mockConstruction(ServiceBusClientBuilder.class, (mock, ctx) -> {
            lenient().when(mock.transportType(any())).thenReturn(mock);
            lenient().when(mock.connectionString(any())).thenReturn(mock);
            lenient().when(mock.proxyOptions(any())).thenReturn(mock);
            var senderBuilder = Mockito.mock(ServiceBusClientBuilder.ServiceBusSenderClientBuilder.class);
            lenient().when(mock.sender()).thenReturn(senderBuilder);
            lenient().when(senderBuilder.buildClient()).thenReturn(Mockito.mock(ServiceBusSenderClient.class));
        })) {
            // When
            AzureServiceBusClientConfig config = new AzureServiceBusClientConfig();
            ServiceBusSenderClient client = config.serviceBusSenderClient(asbConfig, cdpConfig);

            // Then
            assertThat(client).isNotNull();
            ServiceBusClientBuilder constructed = mocked.constructed().get(0);
            verify(constructed).proxyOptions(any(ProxyOptions.class));
        }
    }

    @Test
    void serviceBusSenderClient_shouldNotApplyProxyOptions_whenProxyUrlIsAbsent() {
        // Given
        CdpConfig cdpConfig = new CdpConfig(null, null, null, null, null);

        try (var mocked = Mockito.mockConstruction(ServiceBusClientBuilder.class, (mock, ctx) -> {
            lenient().when(mock.transportType(any())).thenReturn(mock);
            lenient().when(mock.connectionString(any())).thenReturn(mock);
            var senderBuilder = Mockito.mock(ServiceBusClientBuilder.ServiceBusSenderClientBuilder.class);
            lenient().when(mock.sender()).thenReturn(senderBuilder);
            lenient().when(senderBuilder.buildClient()).thenReturn(Mockito.mock(ServiceBusSenderClient.class));
        })) {
            // When
            AzureServiceBusClientConfig config = new AzureServiceBusClientConfig();
            ServiceBusSenderClient client = config.serviceBusSenderClient(asbConfig, cdpConfig);

            // Then
            assertThat(client).isNotNull();
            ServiceBusClientBuilder constructed = mocked.constructed().get(0);
            verify(constructed, never()).proxyOptions(any());
        }
    }
}
