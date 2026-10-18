-- V1: baseline schema of the travelease_audit database, taken from the existing (ddl-auto generated) MySQL
-- schema with SHOW CREATE TABLE; only per-environment AUTO_INCREMENT counters were removed.
-- Existing databases are baselined at V1 (spring.flyway.baseline-on-migrate) and skip this script.

CREATE TABLE IF NOT EXISTS `audit_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` enum('CREATED','UPDATED','APPROVED','REJECTED','RESUBMITTED','REIMBURSED') NOT NULL,
  `actor_id` bigint NOT NULL,
  `after_status` varchar(20) NOT NULL,
  `before_status` varchar(20) DEFAULT NULL,
  `entity_id` bigint NOT NULL,
  `entity_type` enum('TRAVEL_REQUEST','EXPENSE_CLAIM') NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_record_entity_occurred` (`entity_type`,`entity_id`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
