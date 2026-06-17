package uk.gov.defra.cdp.dynamicsgateway.configuration;

import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Slf4j
@Configuration
@EnableConfigurationProperties(NotificationSqsConfig.class)
public class AwsConfig {

    private final String region;
    private final AppAwsConfig appAwsConfig;

    public AwsConfig(
        @Value("${aws.region}") String region,
        AppAwsConfig appAwsConfig) {
        this.region = region;
        this.appAwsConfig = appAwsConfig;
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        SqsAsyncClientBuilder builder = SqsAsyncClient.builder()
            .region(Region.of(region))
            .credentialsProvider(resolveCredentialsProvider())
            .overrideConfiguration(c -> c
                .retryStrategy(RetryMode.ADAPTIVE_V2)
                .apiCallTimeout(Duration.ofSeconds(30)));
        if (hasEndpointOverride()) {
            log.info("Using SQS endpoint override: {}", appAwsConfig.endpointOverride());
            builder.endpointOverride(URI.create(appAwsConfig.endpointOverride()));
        }
        return builder.build();
    }

    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient,
            NotificationSqsConfig sqsConfig) {
        return SqsMessageListenerContainerFactory.builder()
            .configure(options -> options
                .maxConcurrentMessages(sqsConfig.maxMessages())
                .pollTimeout(Duration.ofSeconds(sqsConfig.waitTimeSeconds()))
                .messageVisibility(Duration.ofSeconds(sqsConfig.visibilityTimeoutSeconds())))
            .sqsAsyncClient(sqsAsyncClient)
            .build();
    }

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(resolveCredentialsProvider())
            .overrideConfiguration(c -> c
                .retryStrategy(RetryMode.ADAPTIVE_V2)
                .apiCallTimeout(Duration.ofSeconds(30))
                .apiCallAttemptTimeout(Duration.ofSeconds(10)));
        applyEndpointOverride(builder);
        return builder.build();
    }

    private boolean hasEndpointOverride() {
        return appAwsConfig.endpointOverride() != null
            && !appAwsConfig.endpointOverride().isBlank();
    }

    private boolean hasStaticCredentials() {
        return appAwsConfig.accessKeyId() != null
            && !appAwsConfig.accessKeyId().isBlank()
            && appAwsConfig.secretAccessKey() != null
            && !appAwsConfig.secretAccessKey().isBlank();
    }

    private AwsCredentialsProvider resolveCredentialsProvider() {
        if (hasEndpointOverride() && hasStaticCredentials()) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(appAwsConfig.accessKeyId(), appAwsConfig.secretAccessKey()));
        }
        if (hasEndpointOverride()) {
            log.warn("APP_AWS_ENDPOINT_OVERRIDE is set but static credentials are absent — falling back to DefaultCredentialsProvider");
        }
        return DefaultCredentialsProvider.builder().build();
    }

    private void applyEndpointOverride(S3ClientBuilder builder) {
        if (!hasEndpointOverride()) {
            return;
        }
        log.info("Using S3 endpoint override: {}", appAwsConfig.endpointOverride());
        builder.endpointOverride(URI.create(appAwsConfig.endpointOverride()))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build());
    }
}
