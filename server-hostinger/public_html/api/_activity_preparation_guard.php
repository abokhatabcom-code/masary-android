<?php
declare(strict_types=1);

/**
 * Phase 08 safety adapter around the shared preparation engine.
 *
 * The current Android release links verified smart-guide steps only. Other activity
 * types remain visible as unavailable until their authoritative unlock/attempt
 * adapters are introduced by the dedicated subject and test-engine stages.
 */
function api_activity_authoritative_flow_supported(array $request): bool
{
    return ($request['activity_type'] ?? '') === 'guide_step'
        && in_array((string)($request['source'] ?? ''), ['home_guide', 'guide'], true)
        && (int)($request['guide_step_id'] ?? 0) > 0;
}

function api_activity_apply_authoritative_policy(array $preview): array
{
    $request = is_array($preview['_normalized_request'] ?? null)
        ? $preview['_normalized_request']
        : [];

    // Keep a real blocking reason already produced by the shared engine, such as
    // invalid content or insufficient hearts. Do not replace it with a generic state.
    if (empty($preview['eligibility']['available'])) {
        return $preview;
    }

    if (!api_activity_authoritative_flow_supported($request)) {
        $preview['eligibility'] = [
            'available' => false,
            'status' => 'unavailable',
            'reason' => 'سيُتاح هذا النشاط بعد ربط محرك الفتح والمحاولات المؤكد في مرحلته المختصة.',
            'reason_code' => 'activity_engine_pending',
        ];
    }

    return $preview;
}

function api_activity_require_authoritative_start(array $payload): array
{
    $request = api_activity_normalize_request($payload);
    if (!api_activity_authoritative_flow_supported($request)) {
        api_error(
            'activity_engine_pending',
            'لا يمكن بدء هذا النوع قبل اكتمال ربط محرك الفتح والمحاولات المؤكد.',
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

    $request = api_activity_require_authoritative_start($payload);
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
