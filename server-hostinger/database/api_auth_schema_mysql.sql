-- Masary native apps API authentication schema (MySQL/MariaDB)
-- The runtime API applies this migration automatically. This file is for review/manual deployment only.

CREATE TABLE IF NOT EXISTS `api_schema_migrations` (
  `migration_key` VARCHAR(191) NOT NULL,
  `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`migration_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `api_auth_sessions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `role` VARCHAR(32) NOT NULL,
  `device_name` VARCHAR(120) NOT NULL,
  `access_token_hash` CHAR(64) NOT NULL,
  `refresh_token_hash` CHAR(64) NOT NULL,
  `previous_refresh_token_hash` CHAR(64) NULL,
  `access_expires_at` DATETIME NOT NULL,
  `refresh_expires_at` DATETIME NOT NULL,
  `previous_refresh_expires_at` DATETIME NULL,
  `ip_address` VARCHAR(64) NOT NULL DEFAULT '',
  `user_agent` VARCHAR(255) NOT NULL DEFAULT '',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_used_at` DATETIME NULL,
  `revoked_at` DATETIME NULL,
  `revoked_reason` VARCHAR(64) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_api_auth_access_hash` (`access_token_hash`),
  UNIQUE KEY `uq_api_auth_refresh_hash` (`refresh_token_hash`),
  KEY `idx_api_auth_user_active` (`user_id`, `revoked_at`, `refresh_expires_at`),
  KEY `idx_api_auth_previous_refresh` (`previous_refresh_token_hash`),
  CONSTRAINT `fk_api_auth_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `app_users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `api_auth_events` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id` BIGINT UNSIGNED NULL,
  `user_id` BIGINT UNSIGNED NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `ip_address` VARCHAR(64) NOT NULL DEFAULT '',
  `user_agent` VARCHAR(255) NOT NULL DEFAULT '',
  `metadata_json` LONGTEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_api_auth_events_user` (`user_id`, `created_at`),
  KEY `idx_api_auth_events_session` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `api_schema_migrations` (`migration_key`) VALUES ('api_auth_v1');
