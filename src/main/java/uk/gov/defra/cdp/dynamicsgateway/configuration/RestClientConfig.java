package uk.gov.defra.cdp.dynamicsgateway.configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import uk.gov.defra.cdp.dynamicsgateway.interceptor.TraceIdPropagationInterceptor;

@Configuration
@Slf4j
public class RestClientConfig {

    private final ClientHttpRequestFactory customRequestFactory;
    private final TraceIdPropagationInterceptor traceIdInterceptor;

    public RestClientConfig(
        TraceIdPropagationInterceptor traceIdInterceptor,
        SSLContext customSslContext) {
        log.info("Configuring HTTP clients with custom SSL context and trace ID propagation");

        HttpClient httpClient = HttpClient.newBuilder()
            .sslContext(customSslContext)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));

        this.customRequestFactory = factory;
        this.traceIdInterceptor = traceIdInterceptor;
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        log.debug("Creating RestClient.Builder with custom SSL context and trace ID propagation");
        return RestClient.builder()
            .requestFactory(customRequestFactory)
            .requestInterceptor(traceIdInterceptor);
    }

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public RestTemplateBuilder restTemplateBuilder() {
        log.debug("Creating RestTemplateBuilder with custom SSL context and trace ID propagation");
        return new RestTemplateBuilder()
            .requestFactory(() -> customRequestFactory)
            .interceptors(traceIdInterceptor);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
