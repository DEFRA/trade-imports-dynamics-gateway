package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;
import uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSqsListener implements ApplicationRunner {

    private final SqsClient sqsClient;
    private final QueueMessageSender queueMessageSender;
    private final NotificationSqsConfig sqsConfig;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual()
            .name("notification-sqs-listener")
            .start(this::pollLoop);
        log.info("Notification SQS listener started on queue: {}", sqsConfig.queueUrl());
    }

    private void pollLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                pollOnce();
            } catch (Exception e) {
                log.error("Unexpected error in SQS poll loop: {}", e.getMessage(), e);
            }
        }
    }

    void pollOnce() {
        ReceiveMessageResponse response = sqsClient.receiveMessage(b -> b
            .queueUrl(sqsConfig.queueUrl())
            .maxNumberOfMessages(sqsConfig.maxMessages())
            .waitTimeSeconds(sqsConfig.waitTimeSeconds())
            .visibilityTimeout(sqsConfig.visibilityTimeoutSeconds())
            .attributeNamesWithStrings(MessageSystemAttributeName.MESSAGE_GROUP_ID.toString()));

        for (Message message : response.messages()) {
            processMessage(message);
        }
    }

    private void processMessage(Message message) {
        String messageGroupId = message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);

        if (messageGroupId == null || messageGroupId.isBlank()) {
            log.error("SQS message missing MESSAGE_GROUP_ID, deleting as unroutable: messageId={}", message.messageId());
            deleteMessage(message.receiptHandle());
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(message.body());
            queueMessageSender.publish(payload, messageGroupId);
            deleteMessage(message.receiptHandle());
            log.info("Event forwarded to ASB and deleted from SQS: messageId={}, aggregateId={}", message.messageId(), messageGroupId);
        } catch (JsonProcessingException e) {
            log.error("SQS message body is not valid JSON, deleting as unprocessable: messageId={}, body={}, error={}",
                message.messageId(), message.body(), e.getMessage(), e);
            deleteMessage(message.receiptHandle());
        } catch (Exception e) {
            log.error("Transient failure processing SQS message, leaving for retry: messageId={}, error={}",
                message.messageId(), e.getMessage(), e);
        }
    }

    private void deleteMessage(String receiptHandle) {
        sqsClient.deleteMessage(b -> b
            .queueUrl(sqsConfig.queueUrl())
            .receiptHandle(receiptHandle));
    }
}
