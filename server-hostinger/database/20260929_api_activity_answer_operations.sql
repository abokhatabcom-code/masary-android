-- Phase 13.6 review-only migration.
-- Preserves idempotency history when an admin-enabled back-navigation revision
-- updates the single current answer row for a question.

CREATE TABLE IF NOT EXISTS api_activity_answer_operations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    public_session_id VARCHAR(128) NOT NULL,
    question_id VARCHAR(128) NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    result_json LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_api_activity_answer_operations_user_key (user_id, idempotency_key_hash),
    KEY idx_api_activity_answer_operations_session (user_id, public_session_id),
    KEY idx_api_activity_answer_operations_question (user_id, public_session_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
