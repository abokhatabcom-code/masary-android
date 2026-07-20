<?php
declare(strict_types=1);

require_once __DIR__ . '/_bootstrap.php';

function api_db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }
    $pdo = db();
    api_auth_ensure_schema($pdo);
    return $pdo;
}

function api_auth_ensure_schema(PDO $pdo): void
{
    static $done = false;
    if ($done) {
        return;
    }

    $driver = strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME));
    if ($driver === 'mysql') {
        $pdo->exec("CREATE TABLE IF NOT EXISTS `api_schema_migrations` (
          `migration_key` VARCHAR(191) NOT NULL,
          `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (`migration_key`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    } else {
        $pdo->exec("CREATE TABLE IF NOT EXISTS api_schema_migrations (
          migration_key TEXT PRIMARY KEY,
          applied_at TEXT NOT NULL DEFAULT (datetime('now'))
        )");
    }

    $check = $pdo->prepare('SELECT migration_key FROM api_schema_migrations WHERE migration_key=? LIMIT 1');
    $check->execute(['api_auth_v1']);
    if ($check->fetchColumn()) {
        $done = true;
        return;
    }

    if ($driver === 'mysql') {
        $pdo->exec("CREATE TABLE IF NOT EXISTS `api_auth_sessions` (
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        $pdo->exec("CREATE TABLE IF NOT EXISTS `api_auth_events` (
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    } else {
        $pdo->exec("CREATE TABLE IF NOT EXISTS api_auth_sessions (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          user_id INTEGER NOT NULL,
          role TEXT NOT NULL,
          device_name TEXT NOT NULL,
          access_token_hash TEXT NOT NULL UNIQUE,
          refresh_token_hash TEXT NOT NULL UNIQUE,
          previous_refresh_token_hash TEXT,
          access_expires_at TEXT NOT NULL,
          refresh_expires_at TEXT NOT NULL,
          previous_refresh_expires_at TEXT,
          ip_address TEXT NOT NULL DEFAULT '',
          user_agent TEXT NOT NULL DEFAULT '',
          created_at TEXT NOT NULL DEFAULT (datetime('now')),
          updated_at TEXT NOT NULL DEFAULT (datetime('now')),
          last_used_at TEXT,
          revoked_at TEXT,
          revoked_reason TEXT,
          FOREIGN KEY(user_id) REFERENCES app_users(id) ON DELETE CASCADE
        )");
        $pdo->exec('CREATE INDEX IF NOT EXISTS idx_api_auth_user_active ON api_auth_sessions(user_id, revoked_at, refresh_expires_at)');
        $pdo->exec('CREATE INDEX IF NOT EXISTS idx_api_auth_previous_refresh ON api_auth_sessions(previous_refresh_token_hash)');

        $pdo->exec("CREATE TABLE IF NOT EXISTS api_auth_events (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          session_id INTEGER,
          user_id INTEGER,
          event_type TEXT NOT NULL,
          ip_address TEXT NOT NULL DEFAULT '',
          user_agent TEXT NOT NULL DEFAULT '',
          metadata_json TEXT,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )");
        $pdo->exec('CREATE INDEX IF NOT EXISTS idx_api_auth_events_user ON api_auth_events(user_id, created_at)');
        $pdo->exec('CREATE INDEX IF NOT EXISTS idx_api_auth_events_session ON api_auth_events(session_id, created_at)');
    }

    $insert = $pdo->prepare('INSERT INTO api_schema_migrations(migration_key) VALUES(?)');
    try {
        $insert->execute(['api_auth_v1']);
    } catch (Throwable) {
        // Another request may have applied the same migration concurrently.
    }
    $done = true;
}
