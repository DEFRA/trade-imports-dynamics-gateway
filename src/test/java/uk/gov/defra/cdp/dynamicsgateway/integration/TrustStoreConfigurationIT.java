package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TrustStoreConfigurationIT extends IntegrationBase {

    @Autowired
    private SSLContext customSslContext;

    @Test
    void customSslContext_isTlsAndWiredBySpring() {
        assertThat(customSslContext).isNotNull();
        assertThat(customSslContext.getProtocol()).isEqualTo("TLS");
    }
}
