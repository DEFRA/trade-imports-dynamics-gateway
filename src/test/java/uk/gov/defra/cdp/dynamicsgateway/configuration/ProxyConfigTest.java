package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.ProxySelector;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
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

    @ParameterizedTest
    @ValueSource(strings = {
        "http://proxy-user:s3cret-password@proxy example:3128",
        "proxy-user:s3cret-password@proxy.example:3128",
        "http://proxy-user:s3cret@password@proxy.example:3128"
    })
    void configureProxy_unusableUrlWithCredentials_logsRedactedUrlWithoutCredentialsOrThrowable(String proxyUrl) {
        ReflectionTestUtils.setField(proxyConfig, "httpProxy", proxyUrl);

        List<ILoggingEvent> events = logEventsWhileConfiguringProxy();

        assertThat(System.getProperty("http.proxyHost")).isNull();
        assertThat(events).isNotEmpty().allSatisfy(event -> {
            assertThat(event.getFormattedMessage())
                .contains("***@")
                .doesNotContain("proxy-user")
                .doesNotContain("password");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void redactCredentials_replacesUserInfoAndKeepsSchemeHostAndPort() {
        assertThat(ProxyConfig.redactCredentials("http://user:secret@proxy.example:3128"))
            .isEqualTo("http://***@proxy.example:3128");
    }

    @Test
    void redactCredentials_leavesUrlWithoutCredentialsUnchanged() {
        assertThat(ProxyConfig.redactCredentials("http://proxy.example:3128"))
            .isEqualTo("http://proxy.example:3128");
    }

    private List<ILoggingEvent> logEventsWhileConfiguringProxy() {
        Logger logger = (Logger) LoggerFactory.getLogger(ProxyConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            proxyConfig.configureProxy();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }
}
