package com.travelease.common.outbox;

/** Delivery state of one event to one destination. */
public enum OutboxStatus {
    /** Waiting for (re)delivery. */
    PENDING,
    /** Accepted by the destination (2xx). */
    DELIVERED,
    /** Permanently rejected or out of retry attempts; needs operator attention. */
    FAILED
}
