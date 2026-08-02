<?php
declare(strict_types=1);

function api_activity_session_is_expired(array $row): bool
{
    if (in_array((string)($row['status'] ?? ''), ['expired', 'abandoned'], true)) {
        return true;
    }
    $expiresAt = strtotime((string)($row['expires_at'] ?? ''));
    return $expiresAt !== false && $expiresAt <= time();
}

function api_activity_mark_session_expired(PDO $pdo, array $row): void
{
    $id = (int)($row['id'] ?? 0);
    if ($id <= 0 || (string)($row['status'] ?? '') === 'expired') {
        return;
    }
    $stmt = $pdo->prepare(
        "UPDATE api_activity_sessions SET status='expired',updated_at=? "
        . "WHERE id=? AND status IN ('created','in_progress')",
    );
    $stmt->execute([api_mysql_datetime(time()), $id]);
}

function api_activity_current_response(array $row, bool $replayed = true): array
{
    $result = api_activity_response_from_row($row, $replayed);
    $result['status'] = (string)($row['status'] ?? ($result['status'] ?? 'created'));
    $result['destination'] = (string)($row['destination'] ?? ($result['destination'] ?? 'activity_session_pending_ui'));
    $result['expires_at'] = (string)($row['expires_at'] ?? ($result['expires_at'] ?? ''));
    return $result;
}

function api_activity_reject_expired_or_abandoned(PDO $pdo, ?array $row): void
{
    if (!$row) {
        return;
    }
    $status = (string)($row['status'] ?? '');
    if (api_activity_session_is_expired($row)) {
        api_activity_mark_session_expired($pdo, $row);
        api_error(
            'activity_session_expired',
            'انتهت صلاحية جلسة النشاط. أعد فتح شاشة التجهيز لبدء جلسة جديدة.',
            410,
        );
    }
    if ($status === 'abandoned') {
        api_error(
            'activity_session_abandoned',
            'أُغلقت جلسة النشاط السابقة. أعد فتح شاشة التجهيز.',
            410,
        );
    }
}

function api_activity_start_lifecycle_guarded(
    PDO $pdo,
    array $session,
    array $payload,
    string $rawKey,
): array {
    $studentId = (int)($session['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }
    if (api_activity_table_exists($pdo, 'api_activity_sessions')) {
        $keyHash = api_activity_idempotency_hash(api_activity_idempotency_key($rawKey));
        $existing = api_activity_fetch_idempotency($pdo, $studentId, $keyHash);
        api_activity_reject_expired_or_abandoned($pdo, $existing);
    }
    return api_activity_start_compatible_guarded($pdo, $session, $payload, $rawKey);
}

function api_activity_start_status_compatible(PDO $pdo, array $session, string $rawKey): array
{
    $studentId = (int)($session['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }
    if (!api_activity_table_exists($pdo, 'api_activity_sessions')) {
        api_error('activity_start_not_found', 'لا توجد محاولة بدء مسجلة.', 404);
    }
    $row = api_activity_fetch_idempotency(
        $pdo,
        $studentId,
        api_activity_idempotency_hash(api_activity_idempotency_key($rawKey)),
    );
    if (!$row) {
        api_error('activity_start_not_found', 'لا توجد محاولة بدء مسجلة.', 404);
    }
    api_activity_reject_expired_or_abandoned($pdo, $row);
    return api_activity_current_response($row, true);
}
