package uk.gov.defra.cdp.dynamicsgateway.configuration;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

// Manual wiring rather than spring-cloud-azure autoconfiguration: SAS auth is provisional and subject to SDA review.
// Keeping the client construction here makes it a two-line swap to Managed/Workload Identity when the auth mechanism is settled.
// Once auth is confirmed, migrate to spring-cloud-azure-starter-servicebus and remove this class.
@Slf4j
@Configuration
@EnableConfigurationProperties(AzureServiceBusConfig.class)
public class AzureServiceBusClientConfig {

    @Bean
    public ServiceBusSenderClient serviceBusSenderClient(AzureServiceBusConfig config) {
        Assert.hasText(config.connectionString(),
            "azure.servicebus.connection-string must not be blank — check AZURE_SERVICE_BUS_CONNECTION_STRING");
        Assert.hasText(config.queue(),
            "azure.servicebus.queue must not be blank — check AZURE_SERVICE_BUS_QUEUE");
        log.info("Configuring Azure Service Bus sender for queue: {}", config.queue());
        return new ServiceBusClientBuilder()
            .connectionString(config.connectionString())
            .sender()
            .queueName(config.queue())
            .buildClient();
    }
}
