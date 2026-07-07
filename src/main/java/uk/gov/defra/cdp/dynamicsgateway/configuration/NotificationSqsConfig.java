package uk.gov.defra.cdp.dynamicsgateway.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aws.sqs.notification")
public record NotificationSqsConfig(
    @NotBlank String queueUrl,
    // DLQ URL for the DLQ API. Not @NotBlank: the listener pipeline works without it, and DlqService
    // fails fast with a clear message if an operation is attempted while it is unset.
    String dlqUrl,
    @Min(0) @Max(20) int waitTimeSeconds,
    @Min(1) @Max(10) int maxMessages) {
}
