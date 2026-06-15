package uk.gov.defra.cdp.dynamicsgateway.events;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.DynamicsGatewayException;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueMessageSender {

    private final ServiceBusSenderClient senderClient;
    private final ObjectMapper objectMapper;

    public void publish(JsonNode body) {
        String messageBody;
        try {
            messageBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new DynamicsGatewayException("Failed to serialise event body", e);
        }

        String sessionId = body.path("reference").asText(null);
        if (sessionId == null || sessionId.isBlank()) {
            throw new DynamicsGatewayException("Event body is missing 'reference' — required as session ID for the session-enabled queue");
        }

        try {
            String messageId = UUID.randomUUID().toString();
            ServiceBusMessage message = new ServiceBusMessage(messageBody)
                .setMessageId(messageId)
                .setContentType("application/json")
                .setSessionId(sessionId);
            senderClient.sendMessage(message);
            log.info("Event forwarded to Azure Service Bus, messageId={}, sessionId={}", messageId, sessionId);
        } catch (Exception e) {
            log.error("Failed to forward event to Azure Service Bus: {}", e.getMessage(), e);
            throw new DynamicsGatewayException("Failed to send event to Azure Service Bus", e);
        }
    }
}
