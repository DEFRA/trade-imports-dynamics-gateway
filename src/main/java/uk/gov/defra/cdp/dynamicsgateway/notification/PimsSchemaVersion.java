package uk.gov.defra.cdp.dynamicsgateway.notification;

enum PimsSchemaVersion {
    V0_2_0("0.2.0"),
    V0_3_0("0.3.0");

    private static final String SCHEMA_URI_PREFIX =
        "https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/pims/gbn-ag-pims-v";

    private final String number;

    PimsSchemaVersion(String number) {
        this.number = number;
    }

    String number() {
        return number;
    }

    String schemaUri() {
        return SCHEMA_URI_PREFIX + number + ".schema.json";
    }

    boolean isAtLeast(PimsSchemaVersion minimum) {
        return compareTo(minimum) >= 0;
    }
}
