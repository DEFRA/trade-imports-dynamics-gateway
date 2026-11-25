package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.databind.ObjectMapper;

final class PimsPayloadMappers {

    private PimsPayloadMappers() {}

    static PimsPayloadMapper real(ObjectMapper objectMapper) {
        PimsEnvelopeMapper envelopeMapper = new PimsEnvelopeMapper();
        return new PimsPayloadMapper(
            objectMapper,
            eventMapperV1(envelopeMapper),
            eventMapperV2(envelopeMapper),
            eventMapperV3(envelopeMapper),
            eventMapperV4(envelopeMapper));
    }

    static PimsEventMapper eventMapperV1() {
        return eventMapperV1(new PimsEnvelopeMapper());
    }

    static PimsEventMapperV2 eventMapperV2() {
        return eventMapperV2(new PimsEnvelopeMapper());
    }

    static PimsEventMapperV3 eventMapperV3() {
        return eventMapperV3(new PimsEnvelopeMapper());
    }

    static PimsEventMapperV4 eventMapperV4() {
        return eventMapperV4(new PimsEnvelopeMapper());
    }

    private static PimsEventMapper eventMapperV1(PimsEnvelopeMapper envelopeMapper) {
        return new PimsEventMapper(
            new PimsGbnAgDataMapper(
                new PimsConsignmentMapper(
                    new PimsTransportMapper(),
                    new PimsLineItemMapper()
                )
            ),
            envelopeMapper
        );
    }

    private static PimsEventMapperV2 eventMapperV2(PimsEnvelopeMapper envelopeMapper) {
        PimsCommonMapperV2 commonMapper = new PimsCommonMapperV2();
        return new PimsEventMapperV2(
            new PimsGbnAgDataMapperV2(
                new PimsConsignmentMapperV2(
                    new PimsTransportMapperV2(commonMapper),
                    new PimsLineItemMapperV2(commonMapper),
                    commonMapper
                ),
                commonMapper
            ),
            envelopeMapper
        );
    }

    private static PimsEventMapperV3 eventMapperV3(PimsEnvelopeMapper envelopeMapper) {
        PimsCommonMapperV3 commonMapper = new PimsCommonMapperV3();
        return new PimsEventMapperV3(
            new PimsGbnAgDataMapperV3(consignmentMapperV3(commonMapper), commonMapper),
            envelopeMapper
        );
    }

    private static PimsEventMapperV4 eventMapperV4(PimsEnvelopeMapper envelopeMapper) {
        PimsCommonMapperV3 commonMapper = new PimsCommonMapperV3();
        return new PimsEventMapperV4(
            new PimsGbnAgDataMapperV4(consignmentMapperV3(commonMapper), commonMapper),
            envelopeMapper
        );
    }

    private static PimsConsignmentMapperV3 consignmentMapperV3(PimsCommonMapperV3 commonMapper) {
        return new PimsConsignmentMapperV3(
            new PimsTransportMapperV3(commonMapper),
            new PimsLineItemMapperV3(commonMapper),
            commonMapper
        );
    }
}
