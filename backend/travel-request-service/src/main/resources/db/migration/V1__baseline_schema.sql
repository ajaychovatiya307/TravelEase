-- V1: baseline schema of the travelease_travelrequest database, taken from the existing (ddl-auto generated) MySQL
-- schema with SHOW CREATE TABLE; only per-environment AUTO_INCREMENT counters were removed.
-- Existing databases are baselined at V1 (spring.flyway.baseline-on-migrate) and skip this script.

CREATE TABLE IF NOT EXISTS `travel_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(255) NOT NULL,
  `version` bigint DEFAULT NULL,
  `approver_id` bigint DEFAULT NULL,
  `destination` varchar(255) NOT NULL,
  `employee_id` bigint NOT NULL,
  `end_date` date NOT NULL,
  `justification` varchar(2000) NOT NULL,
  `rejection_reason` varchar(1000) DEFAULT NULL,
  `resubmission_of_id` bigint DEFAULT NULL,
  `start_date` date NOT NULL,
  `status` enum('DRAFT','PENDING','APPROVED','REJECTED') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `travel_request_document` (
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
  `travel_request_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKisibs2ywlf09k9wvtbjma5g2e` (`travel_request_id`),
  CONSTRAINT `FKisibs2ywlf09k9wvtbjma5g2e` FOREIGN KEY (`travel_request_id`) REFERENCES `travel_request` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `approval_decision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `decided_at` datetime(6) NOT NULL,
  `decided_by` bigint NOT NULL,
  `decision` enum('APPROVED','REJECTED') NOT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  `travel_request_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKblnttopbh8ohs3uqnpjubei1e` (`travel_request_id`),
  CONSTRAINT `FKblnttopbh8ohs3uqnpjubei1e` FOREIGN KEY (`travel_request_id`) REFERENCES `travel_request` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
