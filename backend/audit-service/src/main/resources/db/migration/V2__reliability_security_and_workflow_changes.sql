-- V2: Phase 1-3 schema changes (see README "Database migrations").

-- #10/#11/#13 new actions and administrative entity types: native ENUMs widened to VARCHAR (values kept);
-- administrative records carry no workflow status; #12 idempotent delivery (event_id) and change details.
ALTER TABLE `audit_record`
  MODIFY COLUMN `action` varchar(20) NOT NULL,
  MODIFY COLUMN `entity_type` varchar(30) NOT NULL,
  MODIFY COLUMN `after_status` varchar(20) DEFAULT NULL,
  ADD COLUMN `event_id` varchar(36) DEFAULT NULL,
  ADD COLUMN `details` varchar(2000) DEFAULT NULL,
  ADD CONSTRAINT `uk_audit_record_event_id` UNIQUE (`event_id`);
