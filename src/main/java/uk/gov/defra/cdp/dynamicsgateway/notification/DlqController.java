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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API over the notification DLQ.
 *
 * <ul>
 *   <li>{@code GET /dlq/notifications} — list a page of messages + approximate depth</li>
 * </ul>
 *
 * <p>Bulk replay/delete of the whole DLQ (native SQS redrive / purge) is planned but not yet
 * implemented — see the EUDPA-253 plan.
 *
 * @see uk.gov.defra.cdp.dynamicsgateway.filter.AdminSecretFilter
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/dlq/notifications")
@Tag(name = "DLQ API", description = "List notification dead-letter queue messages")
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
}
