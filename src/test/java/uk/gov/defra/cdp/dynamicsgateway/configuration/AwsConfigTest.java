package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import uk.gov.defra.cdp.dynamicsgateway.metrics.ExternalCallRecorder;
import uk.gov.defra.cdp.dynamicsgateway.metrics.LastCallOutcomes;
import uk.gov.defra.cdp.dynamicsgateway.metrics.SqsCallMetrics;

class AwsConfigTest {

    private static final SqsCallMetrics SQS_CALL_METRICS = new SqsCallMetrics(
        new ExternalCallRecorder(new SimpleMeterRegistry()), new LastCallOutcomes());
    private static final String REGION = "eu-west-2";
    private static final String ENDPOINT = "http://localhost:4566";
    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key";

    @Test
    void sqsAsyncClient_shouldBuild_whenNoEndpointOrStaticCredentials() {
        AppAwsConfig appAwsConfig = new AppAwsConfig(null, null, null);
        AwsConfig awsConfig = new AwsConfig(REGION, appAwsConfig);

        SqsAsyncClient client = awsConfig.sqsAsyncClient(SQS_CALL_METRICS);

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void sqsAsyncClient_shouldBuildWithStaticCredentialsAndEndpoint_whenConfigured() {
        AppAwsConfig appAwsConfig = new AppAwsConfig(ENDPOINT, ACCESS_KEY, SECRET_KEY);
        AwsConfig awsConfig = new AwsConfig(REGION, appAwsConfig);

        SqsAsyncClient client = awsConfig.sqsAsyncClient(SQS_CALL_METRICS);

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void s3Client_shouldBuild_whenNoEndpointOrStaticCredentials() {
        AppAwsConfig appAwsConfig = new AppAwsConfig(null, null, null);
        AwsConfig awsConfig = new AwsConfig(REGION, appAwsConfig);

        S3Client client = awsConfig.s3Client();

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void s3Client_shouldBuildWithDefaultCredentials_whenEndpointSetButAccessKeyIsNull() {
        AppAwsConfig appAwsConfig = new AppAwsConfig(ENDPOINT, null, null);
        AwsConfig awsConfig = new AwsConfig(REGION, appAwsConfig);

        S3Client client = awsConfig.s3Client();

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void s3Client_shouldBuildWithDefaultCredentials_whenEndpointSetButAccessKeyIsBlank() {
        AppAwsConfig appAwsConfig = new AppAwsConfig(ENDPOINT, "", SECRET_KEY);
        AwsConfig awsConfig = new AwsConfig(REGION, appAwsConfig);

        S3Client client = awsConfig.s3Client();

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void s3Client_shouldBuildWithDefaultCredentials_whenEndpointSetButSecretKeyIsBlank() {
        AppAwsConfig appAwsConfig = new AppAwsConfig(ENDPOINT, ACCESS_KEY, "");
        AwsConfig awsConfig = new AwsConfig(REGION, appAwsConfig);

        S3Client client = awsConfig.s3Client();

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void sqsAsyncClient_shouldBuildWithDefaultCredentials_whenEndpointSetButSecretKeyIsNull() {
        AppAwsConfig appAwsConfig = new AppAwsConfig(ENDPOINT, ACCESS_KEY, null);
        AwsConfig awsConfig = new AwsConfig(REGION, appAwsConfig);

        SqsAsyncClient client = awsConfig.sqsAsyncClient(SQS_CALL_METRICS);

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void s3Client_shouldBuildWithStaticCredentials_whenEndpointAndBothStaticCredsAreSet() {
        AppAwsConfig appAwsConfig = new AppAwsConfig(ENDPOINT, ACCESS_KEY, SECRET_KEY);
        AwsConfig awsConfig = new AwsConfig(REGION, appAwsConfig);

        S3Client client = awsConfig.s3Client();

        assertThat(client).isNotNull();
        client.close();
    }
}
