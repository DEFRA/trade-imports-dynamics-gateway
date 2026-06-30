package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.GlobalExceptionHandler;
import uk.gov.defra.cdp.dynamicsgateway.filter.AdminSecretFilter;

// The shared-secret filter is exercised by AdminSecretFilterTest; exclude it here so the slice tests
// the controller's own behaviour (@WebMvcTest otherwise registers Filter beans into the chain).
@WebMvcTest(value = DlqController.class,
    excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AdminSecretFilter.class))
@Import(GlobalExceptionHandler.class)
class DlqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DlqService dlqService;

    @Test
    void list_returnsMessagesAndCount_inSnakeCase() throws Exception {
        when(dlqService.list(10)).thenReturn(new DlqListResponse(
            List.of(new DlqMessage("id-1", "group-1", "dedup-1", 3, "{\"key\":\"a\"}")), 5L));

        mockMvc.perform(get("/dlq/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approximate_count").value(5))
            .andExpect(jsonPath("$.messages[0].id").value("id-1"))
            .andExpect(jsonPath("$.messages[0].message_group_id").value("group-1"))
            .andExpect(jsonPath("$.messages[0].approximate_receive_count").value(3));
    }

    @Test
    void list_passesLimitQueryParamToService() throws Exception {
        when(dlqService.list(3)).thenReturn(new DlqListResponse(List.of(), 0L));

        mockMvc.perform(get("/dlq/notifications").param("limit", "3"))
            .andExpect(status().isOk());

        verify(dlqService).list(3);
    }

    @Test
    void replay_callsServiceWithIds_andReturnsOkWithNoBody() throws Exception {
        mockMvc.perform(post("/dlq/notifications/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"id-1\",\"id-2\"]}"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(dlqService).replay(List.of("id-1", "id-2"));
    }

    @Test
    void delete_callsServiceWithIds_andReturnsOkWithNoBody() throws Exception {
        mockMvc.perform(delete("/dlq/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"id-1\"]}"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(dlqService).delete(List.of("id-1"));
    }

    @Test
    void replay_returnsBadRequest_whenIdsEmpty() throws Exception {
        mockMvc.perform(post("/dlq/notifications/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("/problems/bad-request"));

        verifyNoInteractions(dlqService);
    }

    @Test
    void delete_returnsBadRequest_whenIdsEmpty() throws Exception {
        mockMvc.perform(delete("/dlq/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[]}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(dlqService);
    }

    @Test
    void replay_returnsBadRequest_whenBodyMissing() throws Exception {
        mockMvc.perform(post("/dlq/notifications/replay")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(dlqService);
    }
}
