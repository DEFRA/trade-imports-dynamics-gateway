package uk.gov.defra.cdp.dynamicsgateway.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/events")
public class EventsSendController {

    private final QueueMessageSender queueMessageSender;
    private final ObjectMapper objectMapper;

    @Timed("events.publish")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void publishEvent(@RequestBody JsonNode body) throws JsonProcessingException {
        String aggregateId = body.path("aggregateId").asText(null);
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId is required");
        }

        log.info("Received event for forwarding to Azure Service Bus");
        queueMessageSender.publish(objectMapper.writeValueAsString(body), aggregateId);
    }
}
