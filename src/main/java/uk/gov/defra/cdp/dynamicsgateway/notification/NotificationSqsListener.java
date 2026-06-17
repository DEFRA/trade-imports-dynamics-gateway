package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSqsListener {

    private final QueueMessageSender queueMessageSender;
    private final ObjectMapper objectMapper;

    @SqsListener("${aws.sqs.notification.queue-url}")
    public void receive(
            String body,
            @Header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER) String aggregateId) {

        if (aggregateId == null || aggregateId.isBlank()) {
            log.error("SQS message has missing or blank MESSAGE_GROUP_ID, skipping (auto-deleted): body={}", body);
            return;
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            log.error("SQS message body is not valid JSON, skipping (auto-deleted): body={}, error={}",
                body, e.getMessage(), e);
            return;
        }

        // On DynamicsGatewayException or any other exception: method throws →
        // Spring Cloud AWS does NOT delete → visibility timeout expires → retry → DLQ after maxReceiveCount
        queueMessageSender.publish(payload, aggregateId);
        log.info("Event forwarded to ASB: aggregateId={}", aggregateId);
    }
}
