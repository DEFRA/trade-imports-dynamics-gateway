package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.amqp.ProxyAuthenticationType;
import com.azure.core.amqp.ProxyOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceBusProxyOptionsTest {

    @AfterEach
    void clearJvmProxyProperties() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "file:path", "http://proxy.example.com", "not a valid uri"})
    void fromHttpProxyUrl_shouldReturnEmpty_forAbsentOrInvalidProxyUrl(String proxyUrl) {
        assertThat(ServiceBusProxyOptions.fromHttpProxyUrl(proxyUrl)).isEmpty();
    }

    @Test
    void fromHttpProxyUrl_shouldReturnNoneAuthProxy_forValidHttpProxyUrl() {
        ProxyOptions options = ServiceBusProxyOptions.fromHttpProxyUrl("http://localhost:3128").orElseThrow();

        assertThat(options.getAuthentication()).isEqualTo(ProxyAuthenticationType.NONE);
        assertThat(options.isProxyAddressConfigured()).isTrue();
    }

    @Test
    void fromJvmSystemProperties_shouldReturnEmpty_whenProxyHostNotSet() {
        assertThat(ServiceBusProxyOptions.fromJvmSystemProperties()).isEmpty();
    }

    @Test
    void fromJvmSystemProperties_shouldReturnEmpty_whenPortMissing() {
        System.setProperty("http.proxyHost", "localhost");

        assertThat(ServiceBusProxyOptions.fromJvmSystemProperties()).isEmpty();
    }

    @Test
    void fromJvmSystemProperties_shouldReturnNoneAuthProxy_whenHostAndPortSet() {
        System.setProperty("http.proxyHost", "localhost");
        System.setProperty("http.proxyPort", "3128");

        ProxyOptions options = ServiceBusProxyOptions.fromJvmSystemProperties().orElseThrow();

        assertThat(options.getAuthentication()).isEqualTo(ProxyAuthenticationType.NONE);
        assertThat(options.isProxyAddressConfigured()).isTrue();
    }
}
