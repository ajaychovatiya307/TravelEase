package com.travelease.admin.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;
import com.travelease.common.event.DomainEventEnvelope;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes audit events for administrative changes (#13). Events go through the admin-service
 * transactional outbox (audit destination), so a change and its audit record commit together;
 * the acting administrator is captured from the authenticated request by the outbox writer.
 *
 * <p>Metadata must stay non-sensitive: ids, role names, changed field <em>names</em>, thresholds and
 * flags. Never passwords (plain or hashed), tokens, or personal data values such as emails.</p>
 */
@Component
public class AdminAuditEvents {

    private final ApplicationEventPublisher publisher;

    public AdminAuditEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(AuditEntityType entityType, Long entityId, AuditActionType action, Map<String, String> metadata) {
        publisher.publishEvent(new DomainEventEnvelope(entityType.name(), String.valueOf(entityId), action.name(),
                Instant.now()).withMetadata(metadata == null ? Map.of() : new LinkedHashMap<>(metadata)));
    }

    /** As {@link #publish}, for flows without an authenticated request (e.g. completing a forced reset). */
    public void publishAs(Long actorId, AuditEntityType entityType, Long entityId, AuditActionType action,
            Map<String, String> metadata) {
        publisher.publishEvent(new DomainEventEnvelope(entityType.name(), String.valueOf(entityId), action.name(),
                Instant.now()).withMetadata(metadata == null ? Map.of() : new LinkedHashMap<>(metadata))
                .withDelivery(null, actorId));
    }
}
