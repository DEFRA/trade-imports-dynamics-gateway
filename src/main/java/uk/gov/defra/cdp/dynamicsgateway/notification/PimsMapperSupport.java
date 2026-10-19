package uk.gov.defra.cdp.dynamicsgateway.notification;

import java.util.List;
import java.util.function.Function;

final class PimsMapperSupport {

    private PimsMapperSupport() {
    }

    static <A, B> List<B> mapList(List<A> list, Function<A, B> fn) {
        if (list == null) return List.of();
        return list.stream().map(fn).toList();
    }
}
