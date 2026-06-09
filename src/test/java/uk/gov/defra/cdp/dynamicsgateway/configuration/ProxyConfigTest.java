package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ProxySelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class ProxyConfigTest {

    private final ProxyConfig proxyConfig = new ProxyConfig();

    @AfterEach
    void tearDown() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"file:path", "http://proxy.example.com", "not a valid uri"})
    void configureProxy_shouldNotSetProperties_forInvalidOrAbsentProxyUrl(String proxyUrl) {
        ReflectionTestUtils.setField(proxyConfig, "httpProxy", proxyUrl);

        proxyConfig.configureProxy();

        assertThat(System.getProperty("http.proxyHost")).isNull();
    }

    @Test
    void configureProxy_shouldSetAllSystemProperties_whenValidProxyProvided() {
        ReflectionTestUtils.setField(proxyConfig, "httpProxy", "http://proxy.example.com:3128");

        proxyConfig.configureProxy();

        assertThat(System.getProperty("http.proxyHost")).isEqualTo("proxy.example.com");
        assertThat(System.getProperty("http.proxyPort")).isEqualTo("3128");
        assertThat(System.getProperty("https.proxyHost")).isEqualTo("proxy.example.com");
        assertThat(System.getProperty("https.proxyPort")).isEqualTo("3128");
    }

    @Test
    void configureProxy_shouldConfigureProxySelector_whenValidProxyProvided() {
        ReflectionTestUtils.setField(proxyConfig, "httpProxy", "http://proxy.example.com:3128");

        proxyConfig.configureProxy();

        assertThat(ProxySelector.getDefault()).isNotNull();
    }

}
