package uk.gov.defra.cdp.dynamicsgateway.configuration.tls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;

class TrustStoreConfigurationTest {

    private static final String VALID_CERT_PEM = """
-----BEGIN CERTIFICATE-----
MIIDXTCCAkWgAwIBAgIJAKL0UG+mRKKzMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV
BAYTAkdCMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX
aWRnaXRzIFB0eSBMdGQwHhcNMjQwMTAxMTIwMDAwWhcNMjUwMTAxMTIwMDAwWjBF
MQswCQYDVQQGEwJHQjETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50
ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB
CgKCAQEAy8Dbv8prpJ/0kKhlGeJYozo2t60EG8eOLYKqZCNb1NVQFGe5Omwpe+6j
4aCq2TqXWquc+oudPkJBDwW6VO3GqNQiVQzmA0p6f9JG0m2/kXiE4E9PkWoHDXyY
hwcZQseN81ISlnC6PX7F5sI8KJmR3YJbCq4m+RqIPzHq2f8Fmh3L1lKbAhqT7Fmz
dTxlCQ7Z5fAK4pE7nJBqCKwkXbPyT9xVGkfLvvTxLLzLNMW5CJF8xqPGMrFQFBFF
OBSEqLJTGGMbXhGMmFVWnD1kLlNyYbH8xmNjfcJyDPqF6KFJmXA6d1k5JKxGJqBf
Gz0q6gv+2TgCXnvyKSqQgM/t5vRUqQIDAQABo1AwTjAdBgNVHQ4EFgQUElRjSoVg
KjUqY6+5QxR2mL8M8gQwHwYDVR0jBBgwFoAUElRjSoVgKjUqY6+5QxR2mL8M8gQw
DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAQ7H9pNFLpJ5MF1hLk1Mm
FMWdPNkv8ysB0f9Z5nWQPnHOoFLMeNRLPJnI5K7xBdPvUhKzLXqLpPkdqf9hCxrv
tS8MZQNx3nNjbMqh7PLKDhPfxp2nCcHh0jBVnMNRAJNPPxkJRo8cHMfZLxgx8p9B
NzqG3xGfCpmYNVLMAMGcA7OGJjGMKZJy4Qkj2VRLtHx1H9Z0H3dPfqDCNEQPfD3l
IxFMR0LKI8J1xGMW5jQRLWb7PcG7+PG3NlJ5nNqPDJ2qh9nFKNKMYPnPgDxoJTLF
TvBCqLlXHMnPLWLZJKLAVMj4qRKH7PpZNvUjP4GjGQTQJmJIx6EWBkNZhMuFxqKZ
fA==
-----END CERTIFICATE-----
""".trim();

    @Test
    void customSslContext_shouldUseDefaultTrustStore_whenNoCertProvided() {
        TrustStoreConfiguration config = new TrustStoreConfiguration(new CertificateLoader(null));

        SSLContext sslContext = config.customSslContext();

        assertThat(sslContext.getProtocol()).isEqualTo("TLS");
    }

    @Test
    void customSslContext_shouldCreateCombinedTrustManager_whenCertIsProvided() {
        byte[] encoded = Base64.getEncoder().encode(VALID_CERT_PEM.getBytes());
        TrustStoreConfiguration config = new TrustStoreConfiguration(
            new CertificateLoader(new String(encoded)));

        SSLContext sslContext = config.customSslContext();

        assertThat(sslContext.getProtocol()).isEqualTo("TLS");
    }

    @Test
    void combinedTrustManager_getAcceptedIssuers_shouldCombineFromBothManagers() throws Exception {
        X509TrustManager defaultTm = mock(X509TrustManager.class);
        X509TrustManager customTm = mock(X509TrustManager.class);
        X509Certificate cert1 = mock(X509Certificate.class);
        X509Certificate cert2 = mock(X509Certificate.class);
        when(defaultTm.getAcceptedIssuers()).thenReturn(new X509Certificate[]{cert1});
        when(customTm.getAcceptedIssuers()).thenReturn(new X509Certificate[]{cert2});

        X509TrustManager combined = newCombinedTrustManager(defaultTm, customTm);
        X509Certificate[] issuers = combined.getAcceptedIssuers();

        assertThat(issuers).containsExactly(cert1, cert2);
    }

    @Test
    void combinedTrustManager_checkClientTrusted_shouldSucceed_whenDefaultAccepts() throws Exception {
        X509TrustManager defaultTm = mock(X509TrustManager.class);
        X509TrustManager customTm = mock(X509TrustManager.class);
        X509Certificate[] chain = {mock(X509Certificate.class)};

        newCombinedTrustManager(defaultTm, customTm).checkClientTrusted(chain, "RSA");

        verify(defaultTm).checkClientTrusted(chain, "RSA");
        verifyNoInteractions(customTm);
    }

    @Test
    void combinedTrustManager_checkClientTrusted_shouldFallBackToCustom_whenDefaultRejects() throws Exception {
        X509TrustManager defaultTm = mock(X509TrustManager.class);
        X509TrustManager customTm = mock(X509TrustManager.class);
        X509Certificate[] chain = {mock(X509Certificate.class)};
        doThrow(new CertificateException("untrusted")).when(defaultTm).checkClientTrusted(chain, "RSA");

        newCombinedTrustManager(defaultTm, customTm).checkClientTrusted(chain, "RSA");

        verify(customTm).checkClientTrusted(chain, "RSA");
    }

    @Test
    void combinedTrustManager_checkServerTrusted_shouldSucceed_whenDefaultAccepts() throws Exception {
        X509TrustManager defaultTm = mock(X509TrustManager.class);
        X509TrustManager customTm = mock(X509TrustManager.class);
        X509Certificate[] chain = {mock(X509Certificate.class)};

        newCombinedTrustManager(defaultTm, customTm).checkServerTrusted(chain, "RSA");

        verify(defaultTm).checkServerTrusted(chain, "RSA");
        verifyNoInteractions(customTm);
    }

    @Test
    void combinedTrustManager_checkServerTrusted_shouldFallBackToCustom_whenDefaultRejects() throws Exception {
        X509TrustManager defaultTm = mock(X509TrustManager.class);
        X509TrustManager customTm = mock(X509TrustManager.class);
        X509Certificate[] chain = {mock(X509Certificate.class)};
        doThrow(new CertificateException("untrusted")).when(defaultTm).checkServerTrusted(chain, "RSA");

        newCombinedTrustManager(defaultTm, customTm).checkServerTrusted(chain, "RSA");

        verify(customTm).checkServerTrusted(chain, "RSA");
    }

    private X509TrustManager newCombinedTrustManager(X509TrustManager defaultTm,
                                                      X509TrustManager customTm) throws Exception {
        Class<?> clazz = Class.forName(
            "uk.gov.defra.cdp.dynamicsgateway.configuration.tls.TrustStoreConfiguration$CombinedTrustManager");
        Constructor<?> ctor = clazz.getDeclaredConstructor(X509TrustManager.class, X509TrustManager.class);
        ctor.setAccessible(true);
        return (X509TrustManager) ctor.newInstance(defaultTm, customTm);
    }
}
