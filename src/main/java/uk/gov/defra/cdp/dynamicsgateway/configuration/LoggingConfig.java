package uk.gov.defra.cdp.dynamicsgateway.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingConfig {

    private static final String MDC_SERVICE_VERSION = "service.version";

    @Value("${cdp.service-version}")
    private String serviceVersion;

    @PostConstruct
    public void setupServiceVersion() {
        System.setProperty(MDC_SERVICE_VERSION, serviceVersion);
    }
}
