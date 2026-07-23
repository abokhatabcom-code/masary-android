-- Apply only through a reviewed migration. Repository validation never executes SQL.
CREATE TABLE IF NOT EXISTS api_android_push_installations (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 installation_id CHAR(36) NOT NULL, user_id BIGINT UNSIGNED NULL,
 encrypted_fcm_token TEXT NULL, token_hash CHAR(64) NULL,
 app_version VARCHAR(40) NOT NULL, app_build INT UNSIGNED NOT NULL,
 platform VARCHAR(16) NOT NULL, locale VARCHAR(35) NOT NULL, timezone VARCHAR(64) NOT NULL,
 permission_status VARCHAR(24) NOT NULL, last_seen_at DATETIME NOT NULL,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, disabled_at DATETIME NULL,
 UNIQUE KEY uq_android_installation_id (installation_id),
 UNIQUE KEY uq_android_active_token_hash (token_hash),
 KEY idx_android_installation_user (user_id, disabled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
