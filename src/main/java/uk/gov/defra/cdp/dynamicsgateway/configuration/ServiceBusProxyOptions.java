package uk.gov.defra.cdp.dynamicsgateway.configuration;

import com.azure.core.amqp.ProxyAuthenticationType;
import com.azure.core.amqp.ProxyOptions;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Optional;

public final class ServiceBusProxyOptions {

    private ServiceBusProxyOptions() {}

    public static Optional<ProxyOptions> fromHttpProxyUrl(String httpProxy) {
        if (httpProxy == null || httpProxy.isBlank()) {
            return Optional.empty();
        }

        try {
            URI proxyUri = URI.create(httpProxy);
            String host = proxyUri.getHost();
            int port = proxyUri.getPort();
            if (host == null || port == -1) {
                return Optional.empty();
            }

            // CDP sets HTTP_PROXY to localhost:3128 but the sidecar may only accept IPv4 loopback.
            // Manual CONNECT tests succeed via 127.0.0.1 while "localhost" can resolve to ::1.
            if ("localhost".equalsIgnoreCase(host)) {
                host = "127.0.0.1";
            }

            // CDP sidecar does not require app credentials; explicit NONE avoids the Azure AMQP
            // client's system-default proxy path that mishandles proxy challenges.
            return Optional.of(new ProxyOptions(
                ProxyAuthenticationType.NONE,
                new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)),
                null,
                null));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // Fallback for environments where the proxy is configured via JVM system properties
    // (-Dhttp.proxyHost / -Dhttp.proxyPort) rather than the HTTP_PROXY environment variable.
    public static Optional<ProxyOptions> fromJvmSystemProperties() {
        String host = System.getProperty("http.proxyHost");
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        try {
            int port = Integer.parseInt(System.getProperty("http.proxyPort", "-1"));
            if (port == -1) {
                return Optional.empty();
            }
            if ("localhost".equalsIgnoreCase(host)) {
                host = "127.0.0.1";
            }
            return Optional.of(new ProxyOptions(
                ProxyAuthenticationType.NONE,
                new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)),
                null,
                null));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
