-- V1: baseline schema of the travelease_admin database, taken from the existing (ddl-auto generated) MySQL
-- schema with SHOW CREATE TABLE; only per-environment AUTO_INCREMENT counters were removed.
-- Existing databases are baselined at V1 (spring.flyway.baseline-on-migrate) and skip this script.

CREATE TABLE IF NOT EXISTS `user_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(255) NOT NULL,
  `version` bigint DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `email` varchar(255) NOT NULL,
  `full_name` varchar(255) NOT NULL,
  `password_hash` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(255) NOT NULL,
  `version` bigint DEFAULT NULL,
  `role` enum('EMPLOYEE','MANAGER','FINANCE','ADMIN') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role_user_role` (`user_id`,`role`),
  CONSTRAINT `FK7ojmv1m1vrxfl3kvt5bi5ur73` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_manager_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(255) NOT NULL,
  `version` bigint DEFAULT NULL,
  `effective_from` date NOT NULL,
  `employee_id` bigint NOT NULL,
  `manager_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_manager_mapping_employee_id` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `expense_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(255) NOT NULL,
  `version` bigint DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `code` varchar(50) NOT NULL,
  `label` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_expense_category_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `policy_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(255) NOT NULL,
  `version` bigint DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `category_code` varchar(50) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `receipt_threshold_amount` decimal(19,4) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `travel_request_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(255) NOT NULL,
  `version` bigint DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `documents_mandatory` bit(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
