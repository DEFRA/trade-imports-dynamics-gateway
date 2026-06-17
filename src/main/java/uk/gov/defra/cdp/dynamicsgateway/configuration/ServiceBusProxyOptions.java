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
            return buildProxyOptions(host, port);
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    // CDP sets HTTP_PROXY to localhost:3128 but the sidecar may only accept IPv4 loopback.
    // Manual CONNECT tests succeed via 127.0.0.1 while "localhost" can resolve to ::1.
    // CDP sidecar does not require app credentials; explicit NONE avoids the Azure AMQP
    // client's system-default proxy path that mishandles proxy challenges.
    private static Optional<ProxyOptions> buildProxyOptions(String host, int port) {
        if ("localhost".equalsIgnoreCase(host)) {
            host = "127.0.0.1";
        }
        return Optional.of(new ProxyOptions(
            ProxyAuthenticationType.NONE,
            new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)),
            null,
            null));
    }
}
