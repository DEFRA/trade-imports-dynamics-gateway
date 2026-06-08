package uk.gov.defra.cdp.dynamicsgateway.configuration.tls;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TrustStoreConfiguration {

    private final CertificateLoader certificateLoader;

    public TrustStoreConfiguration(CertificateLoader certificateLoader) {
        this.certificateLoader = certificateLoader;
    }

    @Bean
    public SSLContext customSslContext() {
        log.info("Initializing custom SSL context with CDP certificates");

        try {
            X509Certificate cert = certificateLoader.loadCustomCertificate();
            X509TrustManager combinedTrustManager = createCombinedTrustManager(cert);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{combinedTrustManager}, new SecureRandom());

            log.info("Custom SSL context initialized successfully with 1 custom certificate");

            return sslContext;

        } catch (Exception e) {
            log.error("Failed to initialize custom SSL context: {}", e.getMessage(), e);
            throw new IllegalStateException("Cannot initialize SSL context", e);
        }
    }

    private X509TrustManager createCombinedTrustManager(X509Certificate cert) throws Exception {
        TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        defaultTmf.init((KeyStore) null);

        X509TrustManager defaultTrustManager = Arrays.stream(defaultTmf.getTrustManagers())
            .filter(tm -> tm instanceof X509TrustManager)
            .map(tm -> (X509TrustManager) tm)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No default X509TrustManager found"));

        if (cert == null) {
            log.info("No custom certificates found, using default JVM trust store only");
            return defaultTrustManager;
        }

        KeyStore customKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        customKeyStore.load(null, null);
        customKeyStore.setCertificateEntry("TRUSTSTORE_CDP_ROOT_CA", cert);

        log.debug("Added certificate to custom trust store: TRUSTSTORE_CDP_ROOT_CA");

        TrustManagerFactory customTmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        customTmf.init(customKeyStore);

        X509TrustManager customTrustManager = Arrays.stream(customTmf.getTrustManagers())
            .filter(tm -> tm instanceof X509TrustManager)
            .map(tm -> (X509TrustManager) tm)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No custom X509TrustManager found"));

        return new CombinedTrustManager(defaultTrustManager, customTrustManager);
    }

    private static class CombinedTrustManager implements X509TrustManager {

        private final X509TrustManager defaultTrustManager;
        private final X509TrustManager customTrustManager;

        public CombinedTrustManager(X509TrustManager defaultTrustManager,
                                    X509TrustManager customTrustManager) {
            this.defaultTrustManager = defaultTrustManager;
            this.customTrustManager = customTrustManager;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
            try {
                defaultTrustManager.checkClientTrusted(chain, authType);
            } catch (CertificateException e) {
                customTrustManager.checkClientTrusted(chain, authType);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
            try {
                defaultTrustManager.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                customTrustManager.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] defaultIssuers = defaultTrustManager.getAcceptedIssuers();
            X509Certificate[] customIssuers = customTrustManager.getAcceptedIssuers();
            X509Certificate[] combined = new X509Certificate[
                defaultIssuers.length + customIssuers.length];
            System.arraycopy(defaultIssuers, 0, combined, 0, defaultIssuers.length);
            System.arraycopy(customIssuers, 0, combined, defaultIssuers.length, customIssuers.length);
            return combined;
        }
    }
}
