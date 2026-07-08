package uk.gov.defra.cdp.dynamicsgateway.notification;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API over the notification DLQ.
 *
 * <ul>
 *   <li>{@code GET /dlq/notifications} — list a page of messages + approximate depth (open, no auth)</li>
 *   <li>{@code POST /dlq/notifications/replay-all} — move every DLQ message back onto the source queue
 *       (native SQS {@code StartMessageMoveTask}), guarded by the shared-secret header</li>
 *   <li>{@code POST /dlq/notifications/delete-all} — wipe the DLQ (native SQS {@code PurgeQueue}),
 *       guarded by the shared-secret header</li>
 * </ul>
 *
 * <p>There is no per-message replay/delete — only bulk "move everything back" or "wipe everything".
 *
 * @see uk.gov.defra.cdp.dynamicsgateway.filter.AdminSecretFilter
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/dlq/notifications")
@Tag(name = "DLQ API", description = "List, replay-all and delete-all operations on the notification dead-letter queue")
@Validated
public class DlqController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 25;

    private final DlqService dlqService;

    @GetMapping
    @Operation(summary = "List DLQ messages",
        description = "Returns a best-effort snapshot page of dead-letter messages plus the queue's approximate depth")
    @ApiResponse(responseCode = "200", description = "Page of DLQ messages",
        content = @Content(schema = @Schema(implementation = DlqListResponse.class)))
    @ApiResponse(responseCode = "400", description = "limit is below 1 or above " + MAX_LIMIT, content = @Content)
    @Timed("dlq.list")
    public DlqListResponse list(
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit) {
        return dlqService.list(limit);
    }

    @PostMapping("/replay-all")
    @Operation(summary = "Replay all DLQ messages",
        description = "Starts an SQS StartMessageMoveTask moving every DLQ message back onto the source queue")
    @ApiResponse(responseCode = "200", description = "Replay-all task started")
    @ApiResponse(responseCode = "502", description = "A replay or delete-all task is already in progress on this queue", content = @Content)
    @Timed("dlq.replay_all")
    public void replayAll() {
        dlqService.replayAll();
    }

    @PostMapping("/delete-all")
    @Operation(summary = "Delete all DLQ messages",
        description = "Purges the DLQ via SQS PurgeQueue; asynchronous, can take up to 60 seconds to fully complete")
    @ApiResponse(responseCode = "200", description = "Delete-all requested")
    @ApiResponse(responseCode = "502", description = "A purge is already in progress on this queue (60s cooldown)", content = @Content)
    @Timed("dlq.delete_all")
    public void deleteAll() {
        dlqService.deleteAll();
    }
}
