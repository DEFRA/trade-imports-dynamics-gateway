package uk.gov.defra.cdp.dynamicsgateway.notification;

import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Authentication;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Clause;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ExchangedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.GbnAgData;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsAuthentication;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsClause;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsExchangedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsGbnAgData;

@Component
@RequiredArgsConstructor
class PimsGbnAgDataMapper {

    private final PimsConsignmentMapper consignmentMapper;

    PimsGbnAgData mapData(GbnAgData data) {
        if (data == null) return null;
        return new PimsGbnAgData(
            data.model(),
            data.type(),
            mapExchangedDocument(data.exchangedDocument()),
            consignmentMapper.mapSpecifiedConsignment(data.specifiedConsignment())
        );
    }

    private PimsExchangedDocument mapExchangedDocument(ExchangedDocument doc) {
        if (doc == null) return null;
        // issuer and referenceDocument omitted — always null in v0.1.0 (PR #52)
        return new PimsExchangedDocument(
            doc.identifier(),
            doc.traderAssignedId(),
            doc.notificationStatusCode(),
            doc.versionId(),
            doc.issueDateTime(),
            mapAuthentication(doc.firstSignatoryAuthentication())
        );
    }

    private PimsAuthentication mapAuthentication(Authentication auth) {
        if (auth == null) return null;
        return new PimsAuthentication(mapList(auth.includedClause(), this::mapClause));
    }

    private PimsClause mapClause(Clause clause) {
        if (clause == null) return null;
        return new PimsClause(clause.identifier(), clause.content(), clause.urlId());
    }

    private <A, B> List<B> mapList(List<A> list, Function<A, B> fn) {
        if (list == null) return List.of();
        return list.stream().map(fn).toList();
    }
}
