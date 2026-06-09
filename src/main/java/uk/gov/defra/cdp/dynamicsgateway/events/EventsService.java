package uk.gov.defra.cdp.dynamicsgateway.events;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.DynamicsGatewayException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventsService {

    private final ServiceBusSenderClient senderClient;
    private final ObjectMapper objectMapper;

    public void publish(JsonNode body) {
        String messageBody;
        try {
            messageBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new DynamicsGatewayException("Failed to serialise event body", e);
        }

        try {
            ServiceBusMessage message = new ServiceBusMessage(messageBody)
                .setContentType("application/json");
            senderClient.sendMessage(message);
            log.info("Event forwarded to Azure Service Bus");
        } catch (Exception e) {
            log.error("Failed to forward event to Azure Service Bus: {}", e.getMessage(), e);
            throw new DynamicsGatewayException("Failed to send event to Azure Service Bus", e);
        }
    }
}
