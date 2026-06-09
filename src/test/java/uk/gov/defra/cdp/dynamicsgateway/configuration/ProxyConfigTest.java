package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ProxySelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

    @Test
    void configureProxy_shouldNotSetProperties_whenProxyUrlIsNull() {
        ReflectionTestUtils.setField(proxyConfig, "httpProxy", null);

        proxyConfig.configureProxy();

        assertThat(System.getProperty("http.proxyHost")).isNull();
    }

    @Test
    void configureProxy_shouldNotSetProperties_whenProxyUrlIsEmpty() {
        ReflectionTestUtils.setField(proxyConfig, "httpProxy", "");

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

    @Test
    void configureProxy_shouldNotSetProperties_whenUriHasNoHost() {
        // Opaque URI (no authority) → getHost() returns null
        ReflectionTestUtils.setField(proxyConfig, "httpProxy", "file:path");

        proxyConfig.configureProxy();

        assertThat(System.getProperty("http.proxyHost")).isNull();
    }

    @Test
    void configureProxy_shouldNotSetProperties_whenUriHasNoPort() {
        // No port component → getPort() returns -1
        ReflectionTestUtils.setField(proxyConfig, "httpProxy", "http://proxy.example.com");

        proxyConfig.configureProxy();

        assertThat(System.getProperty("http.proxyHost")).isNull();
    }

    @Test
    void configureProxy_shouldNotThrow_whenProxyUrlIsMalformed() {
        // Spaces in URI → URI.create throws IllegalArgumentException, caught silently
        ReflectionTestUtils.setField(proxyConfig, "httpProxy", "not a valid uri");

        proxyConfig.configureProxy();

        assertThat(System.getProperty("http.proxyHost")).isNull();
    }
}
