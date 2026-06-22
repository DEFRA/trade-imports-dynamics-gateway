package uk.gov.defra.cdp.dynamicsgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import uk.gov.defra.cdp.dynamicsgateway.configuration.AppAwsConfig;
import uk.gov.defra.cdp.dynamicsgateway.configuration.CdpConfig;

@SpringBootApplication
@EnableConfigurationProperties({CdpConfig.class, AppAwsConfig.class})
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
