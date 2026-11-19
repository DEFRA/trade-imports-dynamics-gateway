package uk.gov.defra.cdp.dynamicsgateway.notification;

import java.time.Instant;
import java.util.List;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxActor;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEventMetadata;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxStatusChange;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.GbnAgData;

public final class OutboxEventFixtures {

    public static final String DEFAULT_EVENT_ID = "evt-1";
    public static final String DEFAULT_AGGREGATE_TYPE = "Notification";
    public static final String DEFAULT_SUB_TYPE = "GBN-AG";
    public static final String DEFAULT_AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    public static final long DEFAULT_AGGREGATE_VERSION = 1L;
    public static final String DEFAULT_EVENT_TYPE = "uk.gov.defra.imports.notification.NotificationSubmitted";

    private OutboxEventFixtures() {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String eventId = DEFAULT_EVENT_ID;
        private String aggregateType = DEFAULT_AGGREGATE_TYPE;
        private String subType = DEFAULT_SUB_TYPE;
        private String aggregateId = DEFAULT_AGGREGATE_ID;
        private long aggregateVersion = DEFAULT_AGGREGATE_VERSION;
        private String eventType = DEFAULT_EVENT_TYPE;
        private Instant timestamp = Instant.EPOCH;
        private GbnAgData data;
        private OutboxEventMetadata metadata;
        private OutboxActor actor;
        private List<OutboxStatusChange> statusChanges;
        private String organisationId;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder aggregateType(String aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }

        public Builder subType(String subType) {
            this.subType = subType;
            return this;
        }

        public Builder aggregateId(String aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder aggregateVersion(long aggregateVersion) {
            this.aggregateVersion = aggregateVersion;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder data(GbnAgData data) {
            this.data = data;
            return this;
        }

        public Builder metadata(OutboxEventMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder actor(OutboxActor actor) {
            this.actor = actor;
            return this;
        }

        public Builder statusChanges(List<OutboxStatusChange> statusChanges) {
            this.statusChanges = statusChanges;
            return this;
        }

        public Builder organisationId(String organisationId) {
            this.organisationId = organisationId;
            return this;
        }

        public OutboxEvent build() {
            return new OutboxEvent(
                eventId, aggregateType, subType, aggregateId, aggregateVersion, eventType,
                timestamp, data, metadata, actor, statusChanges, organisationId);
        }
    }
}