package uk.gov.defra.cdp.dynamicsgateway.notification;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API over the notification source queue.
 *
 * <ul>
 *   <li>{@code GET /queue/notifications} — the queue's approximate visible and in-flight depth
 *       (open, read-only, no auth)</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/queue/notifications")
@Tag(name = "Queue API", description = "Read the backlog of the notification source queue")
public class NotificationQueueController {

    private final NotificationQueueService notificationQueueService;

    @GetMapping
    @Operation(summary = "Read the source queue's depth",
        description = "Returns the queue's approximate visible and in-flight message counts; eventually consistent")
    @ApiResponse(responseCode = "200", description = "The queue's approximate depth",
        content = @Content(schema = @Schema(implementation = NotificationQueueDepth.class)))
    @ApiResponse(responseCode = "502", description = "SQS could not be read", content = @Content)
    @Timed("queue.depth")
    public NotificationQueueDepth depth() {
        return notificationQueueService.depth();
    }
}
