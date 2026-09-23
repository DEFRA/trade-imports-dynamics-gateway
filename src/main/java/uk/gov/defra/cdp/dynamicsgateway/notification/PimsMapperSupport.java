package uk.gov.defra.cdp.dynamicsgateway.notification;

import java.util.List;
import java.util.function.Function;

/**
 * Mapping helpers with no domain meaning, shared by every PIMS mapper in this package.
 *
 * <p>Static rather than a Spring bean: these are pure functions, so holding them in a bean would
 * force a constructor dependency on classes that need nothing else from it. Domain-level shared
 * mapping belongs in {@link PimsCommonMapperV2} (v0.2.0 leaf shapes) or {@link PimsEnvelopeMapper}
 * (envelope shapes common to both streams) instead.
 */
final class PimsMapperSupport {

    private PimsMapperSupport() {
        // Utility class — not instantiable.
    }

    /**
     * Maps each element of {@code list} through {@code fn}, treating a null list as empty.
     *
     * <p>Returns an empty list rather than null so callers never need a null guard. Where the
     * schema forbids an empty array, the receiving record's component carries
     * {@code @JsonInclude(NON_EMPTY)} so the field is omitted rather than emitted as {@code []} —
     * see {@code PimsTradeLineItem.applicableClassification}.
     */
    static <A, B> List<B> mapList(List<A> list, Function<A, B> fn) {
        if (list == null) return List.of();
        return list.stream().map(fn).toList();
    }
}
