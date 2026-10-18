-- V1: baseline schema of the travelease_expence database, taken from the existing (ddl-auto generated) MySQL
-- schema with SHOW CREATE TABLE; only per-environment AUTO_INCREMENT counters were removed.
-- Existing databases are baselined at V1 (spring.flyway.baseline-on-migrate) and skip this script.

CREATE TABLE IF NOT EXISTS `expense_claim` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(255) NOT NULL,
  `version` bigint DEFAULT NULL,
  `amount` decimal(19,4) NOT NULL,
  `category` varchar(50) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `duplicate_flag` bit(1) NOT NULL,
  `employee_id` bigint NOT NULL,
  `expense_date` date NOT NULL,
  `reimbursed_at` datetime(6) DEFAULT NULL,
  `rejection_reason` varchar(1000) DEFAULT NULL,
  `status` enum('DRAFT','PENDING','APPROVED','REJECTED','REIMBURSED') NOT NULL,
  `travel_request_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `expense_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(255) NOT NULL,
  `version` bigint DEFAULT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_size_bytes` bigint NOT NULL,
  `file_type` enum('PDF','JPG','PNG') NOT NULL,
  `storage_reference` varchar(255) NOT NULL,
  `upload_status` enum('PENDING','CONFIRMED','FAILED') NOT NULL,
  `expense_claim_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgmt4jx0iofc0tscep9j9nthc9` (`expense_claim_id`),
  CONSTRAINT `FKgmt4jx0iofc0tscep9j9nthc9` FOREIGN KEY (`expense_claim_id`) REFERENCES `expense_claim` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
