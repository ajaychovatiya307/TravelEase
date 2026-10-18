package com.travelease.travelrequest.entity;

import java.time.LocalDate;

import com.travelease.common.audit.Auditable;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Travel request aggregate root (data-model.md §3.1; spec.md FR-TR-01–07).
 *
 * <p>Extends {@link Auditable} for the shared audit/soft-delete fields
 * ({@code createdAt/By}, {@code updatedAt/By}, {@code deleted}, {@code version}).
 * {@code employeeId}, {@code approverId} and {@code resubmissionOfId} are plain identifier
 * columns - there is no cross-service JPA association (Constitution §2).</p>
 */
@Entity
@Table(name = "travel_request")
public class TravelRequest extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Logical reference to the owning user (UserAccount.id, owned by admin-service). */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "justification", nullable = false, length = 2000)
    private String justification;

    // VARCHAR (not a native MySQL ENUM) so new statuses never need a column rewrite;
    // existing ENUM columns are converted by Flyway migration V2.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private TravelRequestStatus status;

    /** Resolved approver (logical reference); nullable only while the request is in DRAFT. */
    @Column(name = "approver_id")
    private Long approverId;

    /** Populated only when status = REJECTED (VAL-12). */
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    /** Links a resubmitted request to the original rejected request (FR-APR-03). */
    @Column(name = "resubmission_of_id")
    private Long resubmissionOfId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public TravelRequestStatus getStatus() {
        return status;
    }

    public void setStatus(TravelRequestStatus status) {
        this.status = status;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Long getResubmissionOfId() {
        return resubmissionOfId;
    }

    public void setResubmissionOfId(Long resubmissionOfId) {
        this.resubmissionOfId = resubmissionOfId;
    }
}