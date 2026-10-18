package com.travelease.common.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Outbox persistence (enabled only in event-producing services). */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Oldest-first batch of events due for (re)delivery. */
    List<OutboxEvent> findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(OutboxStatus status, Instant now,
            Pageable pageable);

    List<OutboxEvent> findByEventId(String eventId);

    long countByStatus(OutboxStatus status);
}
