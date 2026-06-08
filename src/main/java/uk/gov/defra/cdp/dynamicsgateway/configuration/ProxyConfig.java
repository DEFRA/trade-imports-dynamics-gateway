package uk.gov.defra.cdp.dynamicsgateway.configuration;

import jakarta.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ProxyConfig {

    @Value("${cdp.proxyUrl}")
    private String httpProxy;

    @PostConstruct
    public void configureProxy() {
        if (httpProxy == null || httpProxy.isEmpty()) {
            log.info("No HTTP_PROXY configured - using direct connections");
            return;
        }

        try {
            URI proxyUri = URI.create(httpProxy);
            String proxyHost = proxyUri.getHost();
            int proxyPort = proxyUri.getPort();

            if (proxyHost == null || proxyPort == -1) {
                log.warn("Invalid HTTP_PROXY format: {}. Expected http://host:port", httpProxy);
                return;
            }

            System.setProperty("http.proxyHost", proxyHost);
            System.setProperty("http.proxyPort", String.valueOf(proxyPort));
            System.setProperty("https.proxyHost", proxyHost);
            System.setProperty("https.proxyPort", String.valueOf(proxyPort));

            ProxySelector.setDefault(ProxySelector.of(
                new InetSocketAddress(proxyHost, proxyPort)
            ));

            log.info("HTTP proxy configured: {}:{}", proxyHost, proxyPort);

        } catch (IllegalArgumentException e) {
            log.error("Failed to parse HTTP_PROXY: {}. Error: {}", httpProxy, e.getMessage());
        }
    }
}
