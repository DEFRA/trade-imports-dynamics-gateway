package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the {@link uk.gov.defra.cdp.dynamicsgateway.filter.AdminSecretFilter} is actually wired in
 * front of the real {@link DlqController} routes — unlike {@code DlqControllerTest}, this slice does
 * NOT exclude the filter, so the secret is enforced end-to-end through the servlet chain. The header
 * value comes from {@code admin.secret} set below.
 */
@WebMvcTest(DlqController.class)
@TestPropertySource(properties = "admin.secret=test-secret")
class DlqControllerSecurityTest {

    private static final String HEADER = "Trade-Imports-Animals-Admin-Secret";
    private static final String SECRET = "test-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DlqService dlqService;

    @Test
    void list_isOpen_withoutSecret() throws Exception {
        when(dlqService.list(10)).thenReturn(new DlqListResponse(List.of(), 0L));

        mockMvc.perform(get("/dlq/notifications"))
            .andExpect(status().isOk());
    }

    @Test
    void replay_isRejected_withoutSecret() throws Exception {
        mockMvc.perform(post("/dlq/notifications/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"id-1\"]}"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(dlqService);
    }

    @Test
    void replay_passesThroughToController_withSecret() throws Exception {
        mockMvc.perform(post("/dlq/notifications/replay")
                .header(HEADER, SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"id-1\"]}"))
            .andExpect(status().isOk());

        verify(dlqService).replay(List.of("id-1"));
    }

    @Test
    void replay_isRejected_withWrongSecret() throws Exception {
        mockMvc.perform(post("/dlq/notifications/replay")
                .header(HEADER, "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"id-1\"]}"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(dlqService);
    }

    @Test
    void delete_isRejected_withoutSecret() throws Exception {
        mockMvc.perform(delete("/dlq/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"id-1\"]}"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(dlqService);
    }

    @Test
    void delete_passesThroughToController_withSecret() throws Exception {
        mockMvc.perform(delete("/dlq/notifications")
                .header(HEADER, SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"id-1\"]}"))
            .andExpect(status().isNoContent());

        verify(dlqService).delete(List.of("id-1"));
    }

    @Test
    void delete_isRejected_withWrongSecret() throws Exception {
        mockMvc.perform(delete("/dlq/notifications")
                .header(HEADER, "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"id-1\"]}"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(dlqService);
    }
}
