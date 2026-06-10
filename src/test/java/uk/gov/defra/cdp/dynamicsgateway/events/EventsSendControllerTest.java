package uk.gov.defra.cdp.dynamicsgateway.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.GlobalExceptionHandler;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.DynamicsGatewayException;

@WebMvcTest(EventsSendController.class)
@Import(GlobalExceptionHandler.class)
class EventsSendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueueMessageSender queueMessageSender;

    @Test
    void post_returnsAccepted_forValidJson() throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"value\"}"))
            .andExpect(status().isAccepted());

        verify(queueMessageSender).publish(any(JsonNode.class));
    }

    @Test
    void post_returnsAccepted_forNestedJson() throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"NotificationSubmitted\",\"data\":{\"id\":\"123\"}}"))
            .andExpect(status().isAccepted());
    }

    @Test
    void post_returnsBadRequest_forMissingBody() throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void post_returnsBadRequest_forMalformedJson() throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void post_returnsUnsupportedMediaType_forPlainText() throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.TEXT_PLAIN)
                .content("not json"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void post_returnsBadGateway_whenServiceBusSendFails() throws Exception {
        doThrow(new DynamicsGatewayException("ASB error")).when(queueMessageSender).publish(any());

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error").exists());
    }
}
