package com.travelease.admin.entity;

import com.travelease.common.audit.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Admin-managed travel-request policy singleton (spec.md VAL-04, FR-TR-02, FR-ADM-02, NFR-04).
 *
 * <p>Extends {@link Auditable} for the shared audit/soft-delete fields
 * ({@code createdAt/By}, {@code updatedAt/By}, {@code deleted}, {@code version};
 * data-model.md §1). This is a singleton configuration: at most one non-deleted
 * active row exists at any time, and {@code documentsMandatory} is the source of
 * truth for the VAL-04 / FR-TR-02 mandate - "at least one supporting document is
 * required only when mandated by policy" - which the Travel-Request Service
 * resolves synchronously at submission time. The MVP default is
 * {@code documentsMandatory = false}; inactive or soft-deleted rows are ignored
 * by that lookup but retained for history (NFR-04).</p>
 */
@Entity
@Table(name = "travel_request_policy")
public class TravelRequestPolicy extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * VAL-04 / FR-TR-02 mandate flag - when {@code true}, submitting a travel
     * request requires at least one supporting document. MVP default is
     * {@code false}; the rule is enforced by the Travel-Request Service, not by
     * a DB check.
     */
    @Column(name = "documents_mandatory", nullable = false)
    private Boolean documentsMandatory;

    /** Inactive policies are ignored by travel-request submission but retained for history. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getDocumentsMandatory() {
        return documentsMandatory;
    }

    public void setDocumentsMandatory(Boolean documentsMandatory) {
        this.documentsMandatory = documentsMandatory;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}