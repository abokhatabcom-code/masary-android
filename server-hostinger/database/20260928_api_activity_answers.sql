-- Phase 13 review-only migration. Do not execute automatically on production.
-- Stores idempotent answers for question sessions. Foreign keys are deferred until
-- production column types are verified.
CREATE TABLE IF NOT EXISTS `api_activity_answers` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `public_session_id` VARCHAR(128) NOT NULL,
  `question_id` CHAR(32) NOT NULL,
  `idempotency_key_hash` CHAR(64) NOT NULL,
  `request_hash` CHAR(64) NOT NULL,
  `answer_json` LONGTEXT NOT NULL,
  `result_json` LONGTEXT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_activity_answer_user_idempotency` (`user_id`, `idempotency_key_hash`),
  UNIQUE KEY `uq_activity_answer_question` (`user_id`, `public_session_id`, `question_id`),
  KEY `idx_activity_answers_session` (`user_id`, `public_session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
