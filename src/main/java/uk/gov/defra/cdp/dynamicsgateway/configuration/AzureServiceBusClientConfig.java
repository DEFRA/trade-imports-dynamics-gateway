package uk.gov.defra.cdp.dynamicsgateway.configuration;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(AzureServiceBusConfig.class)
public class AzureServiceBusClientConfig {

    @Bean
    public ServiceBusSenderClient serviceBusSenderClient(AzureServiceBusConfig config) {
        log.info("Configuring Azure Service Bus sender for queue/topic: {}", config.queueOrTopic());
        return new ServiceBusClientBuilder()
            .connectionString(config.connectionString())
            .sender()
            .queueName(config.queueOrTopic())
            .buildClient();
    }
}
