package uk.gov.defra.cdp.dynamicsgateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private QueueMessageSender queueMessageSender;

    @Test
    void post_returnsAccepted_forValidJson() throws Exception {
        // Given
        String body = "{\"key\":\"value\"}";
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);

        // When & Then
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted());

        verify(queueMessageSender).publish(captor.capture());
        assertThat(captor.getValue()).isEqualTo(objectMapper.readTree(body));
    }

    @Test
    void post_returnsAccepted_forNestedJson() throws Exception {
        // Given
        String body = "{\"eventType\":\"NotificationSubmitted\",\"data\":{\"id\":\"123\"}}";
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);

        // When & Then
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted());

        verify(queueMessageSender).publish(captor.capture());
        assertThat(captor.getValue()).isEqualTo(objectMapper.readTree(body));
    }

    @Test
    void post_returnsBadRequest_forMissingBody() throws Exception {
        // When & Then
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(queueMessageSender);
    }

    @Test
    void post_returnsBadRequest_forMalformedJson() throws Exception {
        // When & Then
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(queueMessageSender);
    }

    @Test
    void post_returnsUnsupportedMediaType_forPlainText() throws Exception {
        // When & Then
        mockMvc.perform(post("/events")
                .contentType(MediaType.TEXT_PLAIN)
                .content("not json"))
            .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(queueMessageSender);
    }

    @Test
    void post_returnsBadGateway_whenServiceBusSendFails() throws Exception {
        // Given
        doThrow(new DynamicsGatewayException("ASB error")).when(queueMessageSender).publish(any());

        // When & Then
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error").exists());
    }
}
