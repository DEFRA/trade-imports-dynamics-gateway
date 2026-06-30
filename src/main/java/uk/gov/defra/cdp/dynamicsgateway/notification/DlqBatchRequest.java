package uk.gov.defra.cdp.dynamicsgateway.notification;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request body for bulk replay/delete — the ids of the DLQ messages to act on.
 *
 * @param ids the message ids (see {@link DlqMessage#id()}); must contain at least one
 */
public record DlqBatchRequest(@NotEmpty List<String> ids) {
}
