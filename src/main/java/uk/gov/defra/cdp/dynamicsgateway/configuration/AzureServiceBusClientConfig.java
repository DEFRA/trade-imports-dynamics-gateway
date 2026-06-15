package uk.gov.defra.cdp.dynamicsgateway.configuration;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Manual wiring rather than spring-cloud-azure autoconfiguration: SAS is the agreed auth mechanism
// per PIMS guidance. Isolating client construction here keeps the auth concern swappable (e.g. to
// Managed Identity) with minimal change if required.
@Slf4j
@Configuration
@EnableConfigurationProperties(AzureServiceBusConfig.class)
public class AzureServiceBusClientConfig {

    @Bean
    public ServiceBusSenderClient serviceBusSenderClient(
            AzureServiceBusConfig config, CdpConfig cdpConfig) {
        var builder = new ServiceBusClientBuilder()
            .transportType(config.transportType())
            .connectionString(config.connectionString());

        ServiceBusProxyOptions.fromHttpProxyUrl(cdpConfig.proxyUrl())
            .ifPresent(proxy -> {
                log.info("Applying explicit Service Bus proxy options");
                builder.proxyOptions(proxy);
            });

        return builder.sender().buildClient();
    }
}
