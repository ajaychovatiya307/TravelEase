-- V2: Phase 1-3 schema changes (see README "Database migrations").

-- #14 password lifecycle: forced change after admin reset, token invalidation (credentials version)
ALTER TABLE `user_account`
  ADD COLUMN `must_change_password` bit(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `credentials_version` int NOT NULL DEFAULT 0,
  ADD COLUMN `password_changed_at` datetime(6) DEFAULT NULL;

-- #12/#13 transactional outbox for administrative audit events
CREATE TABLE IF NOT EXISTS `outbox_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(36) NOT NULL,
  `destination` varchar(40) NOT NULL,
  `payload` longtext NOT NULL,
  `status` varchar(20) NOT NULL,
  `attempts` int NOT NULL,
  `next_attempt_at` datetime(6) NOT NULL,
  `last_error` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_destination` (`event_id`,`destination`),
  KEY `idx_outbox_status_next_attempt` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
