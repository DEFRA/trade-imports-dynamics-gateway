package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sqs.model.PurgeQueueInProgressException;
import software.amazon.awssdk.services.sqs.model.SqsException;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.GlobalExceptionHandler;
import uk.gov.defra.cdp.dynamicsgateway.filter.AdminSecretFilter;

// The shared-secret filter is exercised by AdminSecretFilterTest; exclude it here so the slice tests
// the controller's own behaviour (@WebMvcTest otherwise registers Filter beans into the chain).
@WebMvcTest(value = DlqController.class,
    excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AdminSecretFilter.class))
@Import(GlobalExceptionHandler.class)
class DlqControllerTest {

    private static final String BASE_PATH = "/dlq/notifications";
    private static final String REPLAY_ALL_PATH = BASE_PATH + "/replay-all";
    private static final String DELETE_ALL_PATH = BASE_PATH + "/delete-all";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DlqService dlqService;

    @Test
    void list_returnsMessagesAndCount_inSnakeCase() throws Exception {
        when(dlqService.list(10)).thenReturn(new DlqListResponse(
            List.of(new DlqMessage("id-1", "group-1", "dedup-1", 3, "{\"key\":\"a\"}")), 5L));

        mockMvc.perform(get(BASE_PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approximate_count").value(5))
            .andExpect(jsonPath("$.messages[0].id").value("id-1"))
            .andExpect(jsonPath("$.messages[0].message_group_id").value("group-1"))
            .andExpect(jsonPath("$.messages[0].approximate_receive_count").value(3));
    }

    @Test
    void list_passesLimitQueryParamToService() throws Exception {
        when(dlqService.list(3)).thenReturn(new DlqListResponse(List.of(), 0L));

        mockMvc.perform(get(BASE_PATH).param("limit", "3"))
            .andExpect(status().isOk());

        verify(dlqService).list(3);
    }

    @Test
    void list_returnsBadRequest_whenLimitExceedsMax() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("limit", "100000"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(dlqService);
    }

    @Test
    void list_returnsBadRequest_whenLimitBelowMin() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("limit", "0"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(dlqService);
    }

    @Test
    void replayAll_invokesService() throws Exception {
        mockMvc.perform(post(REPLAY_ALL_PATH))
            .andExpect(status().isAccepted());

        verify(dlqService).replayAll();
    }

    @Test
    void replayAll_succeeds_whenUserIdHeaderSupplied() throws Exception {
        mockMvc.perform(post(REPLAY_ALL_PATH).header("User-Id", "operator-1"))
            .andExpect(status().isAccepted());

        verify(dlqService).replayAll();
    }

    @Test
    void replayAll_returnsBadGateway_whenAnotherMoveTaskIsAlreadyRunning() throws Exception {
        // Matches what the real DlqService throws: join() wraps the SDK exception in a CompletionException.
        doThrow(new CompletionException(SqsException.builder()
            .message("A message move task is already in progress")
            .awsErrorDetails(AwsErrorDetails.builder()
                .errorCode("AWS.SimpleQueueService.MessageMoveTask.LimitExceeded")
                .build())
            .build()))
            .when(dlqService).replayAll();

        mockMvc.perform(post(REPLAY_ALL_PATH))
            .andExpect(status().isBadGateway());
    }

    @Test
    void deleteAll_invokesService() throws Exception {
        mockMvc.perform(post(DELETE_ALL_PATH))
            .andExpect(status().isAccepted());

        verify(dlqService).deleteAll();
    }

    @Test
    void deleteAll_succeeds_whenUserIdHeaderSupplied() throws Exception {
        mockMvc.perform(post(DELETE_ALL_PATH).header("User-Id", "operator-1"))
            .andExpect(status().isAccepted());

        verify(dlqService).deleteAll();
    }

    @Test
    void deleteAll_returnsBadGateway_whenPurgeAlreadyInProgress() throws Exception {
        doThrow(new CompletionException(PurgeQueueInProgressException.builder()
            .message("Purge already in progress")
            .build()))
            .when(dlqService).deleteAll();

        mockMvc.perform(post(DELETE_ALL_PATH))
            .andExpect(status().isBadGateway());
    }
}
