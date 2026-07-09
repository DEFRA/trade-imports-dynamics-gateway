package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
        Mockito.when(dlqService.list(10)).thenReturn(new DlqListResponse(List.of(), 0L));

        mockMvc.perform(get("/dlq/notifications"))
            .andExpect(status().isOk());
    }

    @Test
    void replayAll_isRejected_withoutSecret() throws Exception {
        mockMvc.perform(post("/dlq/notifications/replay-all"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(dlqService);
    }

    @Test
    void replayAll_passesThroughToController_withSecret() throws Exception {
        mockMvc.perform(post("/dlq/notifications/replay-all")
                .header(HEADER, SECRET))
            .andExpect(status().isAccepted());

        verify(dlqService).replayAll();
    }

    @Test
    void replayAll_isRejected_withWrongSecret() throws Exception {
        mockMvc.perform(post("/dlq/notifications/replay-all")
                .header(HEADER, "wrong-secret"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(dlqService);
    }

    @Test
    void deleteAll_isRejected_withoutSecret() throws Exception {
        mockMvc.perform(post("/dlq/notifications/delete-all"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(dlqService);
    }

    @Test
    void deleteAll_passesThroughToController_withSecret() throws Exception {
        mockMvc.perform(post("/dlq/notifications/delete-all")
                .header(HEADER, SECRET))
            .andExpect(status().isAccepted());

        verify(dlqService).deleteAll();
    }

    @Test
    void deleteAll_isRejected_withWrongSecret() throws Exception {
        mockMvc.perform(post("/dlq/notifications/delete-all")
                .header(HEADER, "wrong-secret"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(dlqService);
    }
}