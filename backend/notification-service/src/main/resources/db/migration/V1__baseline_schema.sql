-- V1: baseline schema of the travelease_notification database, taken from the existing (ddl-auto generated) MySQL
-- schema with SHOW CREATE TABLE; only per-environment AUTO_INCREMENT counters were removed.
-- Existing databases are baselined at V1 (spring.flyway.baseline-on-migrate) and skip this script.

CREATE TABLE IF NOT EXISTS `notification_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `delivery_status` enum('PENDING','SENT','FAILED_RETRYING','FAILED_EXHAUSTED') NOT NULL,
  `entity_id` bigint NOT NULL,
  `entity_type` enum('TRAVEL_REQUEST','EXPENSE_CLAIM') NOT NULL,
  `last_attempt_at` datetime(6) DEFAULT NULL,
  `message` varchar(500) DEFAULT NULL,
  `notification_type` enum('APPROVAL','REJECTION','REIMBURSEMENT') NOT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `recipient_id` bigint NOT NULL,
  `retry_count` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_event_status_last_attempt` (`delivery_status`,`last_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
