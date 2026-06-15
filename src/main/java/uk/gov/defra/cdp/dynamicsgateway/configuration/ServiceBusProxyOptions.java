package uk.gov.defra.cdp.dynamicsgateway.configuration;

import com.azure.core.amqp.ProxyAuthenticationType;
import com.azure.core.amqp.ProxyOptions;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Optional;

final class ServiceBusProxyOptions {

    private ServiceBusProxyOptions() {}

    static Optional<ProxyOptions> fromHttpProxyUrl(String httpProxy) {
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

            // CDP sidecar on localhost:3128 does not require app credentials; explicit NONE avoids
            // the Azure AMQP client's system-default proxy path that mishandles proxy challenges.
            return Optional.of(new ProxyOptions(
                ProxyAuthenticationType.NONE,
                new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)),
                null,
                null));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
