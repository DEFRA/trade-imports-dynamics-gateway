package uk.gov.defra.cdp.dynamicsgateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@code queueUrl}/{@code dlqUrl}/{@code dlqArn} are {@code @NotBlank} so a misconfigured deployment
 * fails Spring's property-binding validation at startup, rather than {@code DlqService} discovering a
 * blank value only when an operator first calls the DLQ API.
 */
class NotificationSqsConfigTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static NotificationSqsConfig config(String queueUrl, String dlqUrl, String dlqArn) {
        return new NotificationSqsConfig(queueUrl, dlqUrl, dlqArn, 10, 10);
    }

    @Test
    void isValid_whenAllFieldsPopulated() {
        NotificationSqsConfig config = config(
            "https://sqs.eu-west-2.amazonaws.com/123456789012/notifications.fifo",
            "https://sqs.eu-west-2.amazonaws.com/123456789012/notifications-deadletter.fifo",
            "arn:aws:sqs:eu-west-2:123456789012:notifications-deadletter.fifo");

        assertThat(VALIDATOR.validate(config)).isEmpty();
    }

    @Test
    void isInvalid_whenQueueUrlBlank() {
        Set<ConstraintViolation<NotificationSqsConfig>> violations =
            VALIDATOR.validate(config("", "https://example/dlq", "arn:aws:sqs:eu-west-2:123456789012:dlq"));

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("queueUrl");
    }

    @Test
    void isInvalid_whenDlqUrlBlank() {
        Set<ConstraintViolation<NotificationSqsConfig>> violations = VALIDATOR.validate(config(
            "https://example/queue", "", "arn:aws:sqs:eu-west-2:123456789012:dlq"));

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("dlqUrl");
    }

    @Test
    void isInvalid_whenDlqArnBlank() {
        Set<ConstraintViolation<NotificationSqsConfig>> violations =
            VALIDATOR.validate(config("https://example/queue", "https://example/dlq", ""));

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("dlqArn");
    }
}
