package com.travelease.travelrequest.service;

/**
 * Which travel requests a list call returns, independent of how many roles the caller holds
 * (a MANAGER who is also an EMPLOYEE needs both their own requests and their approval queue).
 */
public enum TravelRequestListScope {
    /** Requests the caller submitted. */
    MINE,
    /** Requests routed to the caller for a decision (MANAGER, or FINANCE fallback approver). */
    APPROVALS,
    /** Every request (FINANCE/ADMIN oversight). */
    ALL
}
