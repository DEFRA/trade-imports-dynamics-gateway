package uk.gov.defra.cdp.dynamicsgateway.notification;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API over the notification DLQ. The list read is open; the bulk replay and delete operations
 * are guarded by the shared-secret filter.
 *
 * <ul>
 *   <li>{@code GET    /dlq/notifications}        — list a page of messages + approximate depth</li>
 *   <li>{@code POST   /dlq/notifications/replay} — re-send the selected messages to the source queue</li>
 *   <li>{@code DELETE /dlq/notifications}        — delete the selected messages</li>
 * </ul>
 *
 * <p>Replay and delete take a body of ids and return {@code 200 OK} with no body once the batch has
 * been processed; ids not found on the DLQ are logged by {@link DlqService} rather than surfaced to
 * the caller.
 *
 * @see uk.gov.defra.cdp.dynamicsgateway.filter.AdminSecretFilter
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/dlq/notifications")
@Tag(name = "DLQ API", description = "List, replay and delete notification dead-letter queue messages")
@Validated
public class DlqController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 200;

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

    @PostMapping("/replay")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Replay DLQ messages",
        description = "Re-sends the selected messages to the source queue then removes them from the DLQ. "
            + "Requires the admin secret header.")
    @ApiResponse(responseCode = "200", description = "Replay batch processed", content = @Content)
    @ApiResponse(responseCode = "400", description = "Request body is missing or ids is empty", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing or invalid admin secret", content = @Content)
    @Timed("dlq.replay")
    public void replay(@Valid @RequestBody DlqBatchRequest request) {
        dlqService.replay(request.ids());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete DLQ messages",
        description = "Removes the selected messages from the DLQ. Requires the admin secret header.")
    @ApiResponse(responseCode = "204", description = "Delete batch processed", content = @Content)
    @ApiResponse(responseCode = "400", description = "Request body is missing or ids is empty", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing or invalid admin secret", content = @Content)
    @Timed("dlq.delete")
    public void delete(@Valid @RequestBody DlqBatchRequest request) {
        dlqService.delete(request.ids());
    }
}
