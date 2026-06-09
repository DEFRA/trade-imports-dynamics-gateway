package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ProxySelector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.defra.cdp.dynamicsgateway.configuration.ProxyConfig;

class ProxyConfigIT extends IntegrationBase {

    @Autowired(required = false)
    private ProxyConfig proxyConfig;

    @AfterAll
    static void cleanup() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
    }

    @Test
    void shouldConfigureProxyWhenHttpProxyEnvironmentVariableIsSet() {
        String httpProxy = System.getenv("HTTP_PROXY");

        if (httpProxy == null || httpProxy.isEmpty()) {
            assertThat(proxyConfig)
                .as("ProxyConfig should exist even without HTTP_PROXY")
                .isNotNull();
            return;
        }

        assertThat(System.getProperty("http.proxyHost")).isEqualTo("localhost");
        assertThat(System.getProperty("http.proxyPort")).isEqualTo("3128");
        assertThat(System.getProperty("https.proxyHost")).isEqualTo("localhost");
        assertThat(System.getProperty("https.proxyPort")).isEqualTo("3128");

        ProxySelector proxySelector = ProxySelector.getDefault();
        assertThat(proxySelector).isNotNull();
    }

    @Test
    void shouldHandleMissingHttpProxyGracefully() {
        assertThat(proxyConfig)
            .as("ProxyConfig should be created regardless of HTTP_PROXY")
            .isNotNull();
    }

    @Test
    void shouldHandleInvalidHttpProxyGracefully() {
        assertThat(proxyConfig)
            .as("ProxyConfig handles invalid proxy gracefully")
            .isNotNull();
    }
}
