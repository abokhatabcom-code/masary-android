<?php
declare(strict_types=1);

/**
 * Phase 08 safety adapter around the shared preparation engine.
 *
 * Only smart-guide actions whose type can be derived from server-owned guide data
 * are startable in this phase. Unit tests remain unavailable until the authoritative
 * attempts adapter is connected by the dedicated test-engine stage.
 */
function api_activity_from_action_key(string $actionKey): array
{
    $normalized = strtolower(trim($actionKey));
    if (str_contains($normalized, 'speed')) {
        return ['activity_type' => 'speed_test', 'activity_mode' => 'speed'];
    }
    if (str_contains($normalized, 'mistake')
        || str_contains($normalized, 'error')
        || str_contains($normalized, 'review')) {
        return ['activity_type' => 'review', 'activity_mode' => 'review'];
    }
    if (str_contains($normalized, 'test')
        || str_contains($normalized, 'quiz')
        || str_contains($normalized, 'attempt')) {
        return ['activity_type' => 'unit_test', 'activity_mode' => 'test'];
    }
    return ['activity_type' => 'guide_step', 'activity_mode' => 'learn'];
}

function api_activity_authoritative_guide_action(
    PDO $pdo,
    int $studentId,
    array $request,
): ?array {
    if (!in_array((string)($request['source'] ?? ''), ['home_guide', 'guide'], true)
        || (int)($request['guide_step_id'] ?? 0) <= 0
        || !function_exists('api_student_home_smart_guide')) {
        return null;
    }

    $guide = api_student_home_smart_guide($pdo, $studentId);
    foreach ((array)($guide['steps'] ?? []) as $step) {
        if ((int)($step['id'] ?? 0) !== (int)$request['guide_step_id']) {
            continue;
        }
        $stepUnitId = ((int)($step['unit_id'] ?? 0)) ?: null;
        if ((int)($step['subject_version_id'] ?? 0) !== (int)$request['subject_version_id']
            || $stepUnitId !== ($request['unit_id'] ?? null)
            || (string)($step['progress_state'] ?? 'pending') === 'completed') {
            return null;
        }
        return api_activity_from_action_key((string)($step['action_key'] ?? ''));
    }
    return null;
}

function api_activity_authoritative_flow_supported(
    PDO $pdo,
    int $studentId,
    array $request,
): bool {
    $expected = api_activity_authoritative_guide_action($pdo, $studentId, $request);
    if ($expected === null) {
        return false;
    }

    // The six-attempt rule must come from the test engine, not from Android or a guess.
    if ($expected['activity_type'] === 'unit_test') {
        return false;
    }

    return hash_equals((string)$expected['activity_type'], (string)($request['activity_type'] ?? ''))
        && hash_equals((string)$expected['activity_mode'], (string)($request['activity_mode'] ?? ''));
}

function api_activity_apply_authoritative_policy(
    PDO $pdo,
    int $studentId,
    array $preview,
): array {
    $request = is_array($preview['_normalized_request'] ?? null)
        ? $preview['_normalized_request']
        : [];

    // Keep a real blocking reason already produced by the shared engine, such as
    // invalid content or insufficient hearts. Do not replace it with a generic state.
    if (empty($preview['eligibility']['available'])) {
        return $preview;
    }

    if (!api_activity_authoritative_flow_supported($pdo, $studentId, $request)) {
        $unitTestPending = ($request['activity_type'] ?? '') === 'unit_test';
        $preview['eligibility'] = [
            'available' => false,
            'status' => 'unavailable',
            'reason' => $unitTestPending
                ? 'سيُتاح اختبار الوحدة بعد ربط سجل المحاولات المؤكد.'
                : 'لم يعد نوع هذه الخطوة مطابقًا لخطة الموجّه الحالية.',
            'reason_code' => $unitTestPending ? 'attempt_engine_pending' : 'invalid_guide_action',
        ];
    }

    return $preview;
}

function api_activity_require_authoritative_start(
    PDO $pdo,
    int $studentId,
    array $payload,
): array {
    $request = api_activity_normalize_request($payload);
    if (!api_activity_authoritative_flow_supported($pdo, $studentId, $request)) {
        $unitTestPending = ($request['activity_type'] ?? '') === 'unit_test';
        api_error(
            $unitTestPending ? 'attempt_engine_pending' : 'invalid_guide_action',
            $unitTestPending
                ? 'لا يمكن بدء اختبار الوحدة قبل ربط سجل المحاولات المؤكد.'
                : 'نوع النشاط لا يطابق خطوة الموجّه الحالية.',
            409,
        );
    }
    return $request;
}

function api_activity_start_lock_name(int $studentId, string $requestHash): string
{
    return 'masary_activity_' . substr(hash('sha256', $studentId . '|' . $requestHash), 0, 40);
}

function api_activity_acquire_start_lock(PDO $pdo, string $name): bool
{
    if (strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME)) !== 'mysql') {
        return true;
    }

    $stmt = $pdo->prepare('SELECT GET_LOCK(?, 5)');
    $stmt->execute([$name]);
    return (int)$stmt->fetchColumn() === 1;
}

function api_activity_release_start_lock(PDO $pdo, string $name): void
{
    if (strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME)) !== 'mysql') {
        return;
    }

    try {
        $stmt = $pdo->prepare('SELECT RELEASE_LOCK(?)');
        $stmt->execute([$name]);
    } catch (Throwable) {
        // The database connection also releases advisory locks automatically.
    }
}

/**
 * Run preview validation, idempotency lookup and session creation under one outer
 * transaction. The advisory lock serializes two distinct idempotency keys targeting
 * the same student/activity, while the table constraint protects identical keys.
 */
function api_activity_start_guarded(
    PDO $pdo,
    array $session,
    array $payload,
    string $rawKey,
): array {
    $studentId = (int)($session['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }

    $request = api_activity_require_authoritative_start($pdo, $studentId, $payload);
    $requestHash = api_activity_request_hash($request);
    $lockName = api_activity_start_lock_name($studentId, $requestHash);

    if (!api_activity_acquire_start_lock($pdo, $lockName)) {
        api_error('activity_start_busy', 'هناك محاولة بدء قيد المعالجة. حاول مجددًا بعد لحظات.', 409);
    }

    $ownsTransaction = !$pdo->inTransaction();
    $cleanupPending = true;
    register_shutdown_function(
        static function () use ($pdo, $lockName, $ownsTransaction, &$cleanupPending): void {
            if (!$cleanupPending) {
                return;
            }
            if ($ownsTransaction && $pdo->inTransaction()) {
                try {
                    $pdo->rollBack();
                } catch (Throwable) {
                    // Connection shutdown still discards an uncommitted transaction.
                }
            }
            api_activity_release_start_lock($pdo, $lockName);
        },
    );

    try {
        if ($ownsTransaction) {
            $pdo->beginTransaction();
        }

        // api_activity_start sees the outer transaction and therefore does not commit
        // independently. Its preview and all rechecks now belong to the same unit.
        $result = api_activity_start($pdo, $session, $request, $rawKey);

        if ($ownsTransaction && $pdo->inTransaction()) {
            $pdo->commit();
        }
        return $result;
    } catch (Throwable $error) {
        if ($ownsTransaction && $pdo->inTransaction()) {
            $pdo->rollBack();
        }
        throw $error;
    } finally {
        $cleanupPending = false;
        api_activity_release_start_lock($pdo, $lockName);
    }
}
