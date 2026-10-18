-- V2: Phase 1-3 schema changes (see README "Database migrations").

-- #12 idempotent delivery: one notification per recipient per source event
ALTER TABLE `notification_event`
  ADD COLUMN `source_event_id` varchar(36) DEFAULT NULL,
  ADD CONSTRAINT `uk_notification_source_event_recipient` UNIQUE (`source_event_id`,`recipient_id`);
