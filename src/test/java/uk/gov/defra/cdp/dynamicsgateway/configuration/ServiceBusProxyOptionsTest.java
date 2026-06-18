package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.amqp.ProxyAuthenticationType;
import com.azure.core.amqp.ProxyOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceBusProxyOptionsTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "file:path", "http://proxy.example.com", "not a valid uri", "http://[invalid"})
    void fromHttpProxyUrl_shouldReturnEmpty_forAbsentOrInvalidProxyUrl(String proxyUrl) {
        assertThat(ServiceBusProxyOptions.fromHttpProxyUrl(proxyUrl)).isEmpty();
    }

    @Test
    void fromHttpProxyUrl_shouldReturnNoneAuthProxy_forValidHttpProxyUrl() {
        ProxyOptions options = ServiceBusProxyOptions.fromHttpProxyUrl("http://localhost:3128").orElseThrow();

        assertThat(options.getAuthentication()).isEqualTo(ProxyAuthenticationType.NONE);
        assertThat(options.isProxyAddressConfigured()).isTrue();
    }

}
