package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.sqs.model.SqsException;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.GlobalExceptionHandler;
import uk.gov.defra.cdp.dynamicsgateway.filter.AdminSecretFilter;

@WebMvcTest(value = NotificationQueueController.class,
    excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AdminSecretFilter.class))
@Import(GlobalExceptionHandler.class)
class NotificationQueueControllerTest {

    private static final String PATH = "/queue/notifications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationQueueService notificationQueueService;

    @Test
    void depth_shouldReturnBothCountsInSnakeCase() throws Exception {
        when(notificationQueueService.depth()).thenReturn(new NotificationQueueDepth(5L, 2L));

        mockMvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approximate_count").value(5))
            .andExpect(jsonPath("$.approximate_in_flight_count").value(2));
    }

    @Test
    void depth_shouldReturnBadGateway_whenSqsFails() throws Exception {
        when(notificationQueueService.depth()).thenThrow(new CompletionException(
            SqsException.builder().message("queue unavailable").build()));

        mockMvc.perform(get(PATH))
            .andExpect(status().isBadGateway());
    }
}
