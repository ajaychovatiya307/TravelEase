-- V2: Phase 1-3 schema changes (see README "Database migrations").

-- #12 transactional outbox
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
