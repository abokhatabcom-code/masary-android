-- Review and apply manually in the intended non-production rollout; repository checks never execute SQL.
CREATE TABLE IF NOT EXISTS api_android_push_devices (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT UNSIGNED NOT NULL,
 token TEXT NULL,
 token_hash CHAR(64) NOT NULL,
 platform VARCHAR(16) NOT NULL,
 app_name VARCHAR(32) NOT NULL,
 enabled TINYINT(1) NOT NULL DEFAULT 1,
 created_at DATETIME NOT NULL,
 updated_at DATETIME NOT NULL,
 UNIQUE KEY uq_android_push_token_hash (token_hash),
 KEY idx_android_push_user_enabled (user_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
