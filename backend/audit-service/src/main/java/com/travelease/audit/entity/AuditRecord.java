package com.travelease.audit.entity;

import java.time.Instant;

import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Immutable, append-only audit record (data-model.md §6.1; spec.md FR-AUD-01).
 *
 * <p>Because a record is written once and never modified or deleted, this entity does NOT
 * extend {@code Auditable}: there is no soft-delete ({@code deleted}), no
 * {@code createdAt/updatedAt} audit columns, and no {@code @Version}. Append-only
 * behaviour is enforced at the repository layer, not by the mapping.</p>
 *
 * <p>{@code entityId} and {@code actorId} are plain identifier columns - there is no
 * cross-service JPA association (Constitution §2); {@code actorId} is a logical
 * reference to {@code UserAccount.id} (FR-AUD-01). {@code beforeStatus} is
 * deliberately nullable: e.g. a CREATED action has no before-status.</p>
 */
@Entity
@Table(name = "audit_record", indexes = {
        @Index(name = "idx_audit_record_entity_occurred", columnList = "entity_type, entity_id, occurred_at")
})
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)  // VARCHAR, not a native ENUM: new entity types need no column rewrite
    @Column(name = "entity_type", nullable = false, length = 30)
    private AuditEntityType entityType;

    /** Logical reference to the audited entity in its owning service; no cross-service JPA association. */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** Logical reference to the acting user (UserAccount.id); required per FR-AUD-01. */
    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)  // VARCHAR, not a native ENUM: new actions need no column rewrite
    @Column(name = "action", nullable = false, length = 20)
    private AuditActionType action;

    /** Nullable on purpose - e.g. a CREATED action has no before-status. */
    @Column(name = "before_status", length = 20)
    private String beforeStatus;

    /** Nullable: administrative actions (role grants, password resets, ...) have no workflow status. */
    @Column(name = "after_status", length = 20)
    private String afterStatus;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(AuditEntityType entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public AuditActionType getAction() {
        return action;
    }

    public void setAction(AuditActionType action) {
        this.action = action;
    }

    public String getBeforeStatus() {
        return beforeStatus;
    }

    public void setBeforeStatus(String beforeStatus) {
        this.beforeStatus = beforeStatus;
    }

    public String getAfterStatus() {
        return afterStatus;
    }

    public void setAfterStatus(String afterStatus) {
        this.afterStatus = afterStatus;
    }

    /**
     * Producer-assigned event id (outbox); unique, so a re-delivered event never creates a second
     * record. Null for records ingested before event ids existed.
     */
    @Column(name = "event_id", length = 36, unique = true)
    private String eventId;

    /** Non-sensitive change details (e.g. "roles=EMPLOYEE,MANAGER; fields=email"), never secrets. */
    @Column(name = "details", length = 2000)
    private String details;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}