<?php
declare(strict_types=1);

const API_ACTIVITY_MAX_UNIT_ATTEMPTS = 6;
const API_ACTIVITY_REQUIRED_TEST_TYPES = 5;
const API_ACTIVITY_REVIEW_UNLOCK_PERCENT = 30.0;

function api_activity_guard_table_columns(PDO $pdo, string $table): array
{
    static $cache = [];
    $allowed = [
        'units',
        'lessons',
        'subject_versions',
        'student_unit_points',
        'attempts_unit_tests',
        'student_lesson_progress',
        'lesson_progress',
    ];
    if (!in_array($table, $allowed, true)) {
        return [];
    }
    $driver = strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME));
    $key = $driver . ':' . $table;
    if (isset($cache[$key])) {
        return $cache[$key];
    }
    if (!api_activity_table_exists($pdo, $table)) {
        return $cache[$key] = [];
    }
    try {
        if ($driver === 'sqlite') {
            $rows = $pdo->query("PRAGMA table_info(`{$table}`)")->fetchAll(PDO::FETCH_ASSOC) ?: [];
            return $cache[$key] = array_fill_keys(
                array_map(static fn(array $row): string => (string)($row['name'] ?? ''), $rows),
                true,
            );
        }
        $rows = $pdo->query("SHOW COLUMNS FROM `{$table}`")->fetchAll(PDO::FETCH_ASSOC) ?: [];
        return $cache[$key] = array_fill_keys(
            array_map(static fn(array $row): string => (string)($row['Field'] ?? ''), $rows),
            true,
        );
    } catch (Throwable) {
        return $cache[$key] = [];
    }
}

function api_activity_from_action_key(string $actionKey): array
{
    $normalized = strtolower(trim($actionKey));
    if (str_contains($normalized, 'speed')) {
        return ['activity_type' => 'speed_test', 'activity_mode' => 'speed'];
    }
    if (str_contains($normalized, 'mistake')
        || str_contains($normalized, 'error')) {
        return ['activity_type' => 'smart_review', 'activity_mode' => 'review'];
    }
    if (str_contains($normalized, 'review')) {
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

function api_activity_unit_scope(PDO $pdo, int $subjectVersionId): ?array
{
    $unitColumns = api_activity_guard_table_columns($pdo, 'units');
    if (isset($unitColumns['subject_version_id'])) {
        return ['column' => 'subject_version_id', 'value' => $subjectVersionId];
    }
    if (!isset($unitColumns['subject_id'])) {
        return null;
    }
    $versionColumns = api_activity_guard_table_columns($pdo, 'subject_versions');
    if (!isset($versionColumns['subject_id'])) {
        return null;
    }
    $stmt = $pdo->prepare('SELECT subject_id FROM subject_versions WHERE id=? LIMIT 1');
    $stmt->execute([$subjectVersionId]);
    $subjectId = (int)($stmt->fetchColumn() ?: 0);
    return $subjectId > 0 ? ['column' => 'subject_id', 'value' => $subjectId] : null;
}

function api_activity_previous_unit_id(
    PDO $pdo,
    int $subjectVersionId,
    int $unitId,
): array {
    $scope = api_activity_unit_scope($pdo, $subjectVersionId);
    if ($scope === null) {
        return ['available' => false, 'previous_unit_id' => null];
    }
    $columns = api_activity_guard_table_columns($pdo, 'units');
    $orderColumn = null;
    foreach (['unit_order', 'sort_order', 'position', 'id'] as $candidate) {
        if (isset($columns[$candidate])) {
            $orderColumn = $candidate;
            break;
        }
    }
    if ($orderColumn === null) {
        return ['available' => false, 'previous_unit_id' => null];
    }

    $scopeColumn = $scope['column'];
    $current = $pdo->prepare(
        "SELECT `{$orderColumn}` FROM units WHERE id=? AND `{$scopeColumn}`=? LIMIT 1",
    );
    $current->execute([$unitId, $scope['value']]);
    $currentOrder = $current->fetchColumn();
    if ($currentOrder === false) {
        return ['available' => false, 'previous_unit_id' => null];
    }

    $previous = $pdo->prepare(
        "SELECT id FROM units WHERE `{$scopeColumn}`=? "
        . "AND (`{$orderColumn}` < ? OR (`{$orderColumn}` = ? AND id < ?)) "
        . "ORDER BY `{$orderColumn}` DESC, id DESC LIMIT 1",
    );
    $previous->execute([$scope['value'], $currentOrder, $currentOrder, $unitId]);
    $previousId = $previous->fetchColumn();
    return [
        'available' => true,
        'previous_unit_id' => $previousId === false ? null : (int)$previousId,
    ];
}

function api_activity_completed_attempt_numbers(PDO $pdo, int $studentId, int $unitId): array
{
    $columns = api_activity_guard_table_columns($pdo, 'attempts_unit_tests');
    foreach (['student_id', 'unit_id', 'test_type', 'attempt_number', 'is_completed'] as $required) {
        if (!isset($columns[$required])) {
            return ['available' => false, 'attempts' => []];
        }
    }

    $stmt = $pdo->prepare(
        'SELECT attempt_number, test_type, is_completed FROM attempts_unit_tests '
        . 'WHERE student_id=? AND unit_id=?',
    );
    $stmt->execute([$studentId, $unitId]);
    $typesByAttempt = [];
    foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
        if (empty($row['is_completed'])) {
            continue;
        }
        $attempt = max(0, (int)($row['attempt_number'] ?? 0));
        $type = trim((string)($row['test_type'] ?? ''));
        if ($attempt <= 0 || $type === '') {
            continue;
        }
        $typesByAttempt[$attempt][$type] = true;
    }
    $completed = [];
    foreach ($typesByAttempt as $attempt => $types) {
        if (count($types) >= API_ACTIVITY_REQUIRED_TEST_TYPES) {
            $completed[] = (int)$attempt;
        }
    }
    sort($completed, SORT_NUMERIC);
    return ['available' => true, 'attempts' => $completed];
}

function api_activity_unit_points_row(PDO $pdo, int $studentId, int $unitId): array
{
    $columns = api_activity_guard_table_columns($pdo, 'student_unit_points');
    if (!isset($columns['student_id'], $columns['unit_id'])) {
        return ['available' => false, 'columns' => $columns, 'row' => null];
    }
    $stmt = $pdo->prepare('SELECT * FROM student_unit_points WHERE student_id=? AND unit_id=? LIMIT 1');
    $stmt->execute([$studentId, $unitId]);
    return [
        'available' => true,
        'columns' => $columns,
        'row' => $stmt->fetch(PDO::FETCH_ASSOC) ?: null,
    ];
}

function api_activity_unit_attempts(PDO $pdo, int $studentId, int $unitId): array
{
    $completed = api_activity_completed_attempt_numbers($pdo, $studentId, $unitId);
    $points = api_activity_unit_points_row($pdo, $studentId, $unitId);
    if (empty($completed['available']) && empty($points['available'])) {
        return [
            'available' => false,
            'used' => null,
            'remaining' => null,
            'maximum' => API_ACTIVITY_MAX_UNIT_ATTEMPTS,
            'reason' => 'تعذر قراءة سجل المحاولات المؤكد.',
        ];
    }

    $used = !empty($completed['available']) ? count($completed['attempts']) : 0;
    if (!empty($points['available'])
        && isset($points['columns']['unit_attempts'])
        && is_array($points['row'])) {
        $used = max($used, max(0, (int)($points['row']['unit_attempts'] ?? 0)));
    }
    $used = min(API_ACTIVITY_MAX_UNIT_ATTEMPTS, $used);
    return [
        'available' => true,
        'used' => $used,
        'remaining' => max(0, API_ACTIVITY_MAX_UNIT_ATTEMPTS - $used),
        'maximum' => API_ACTIVITY_MAX_UNIT_ATTEMPTS,
        'reason' => '',
    ];
}

function api_activity_first_attempt_complete(PDO $pdo, int $studentId, int $unitId): array
{
    $completed = api_activity_completed_attempt_numbers($pdo, $studentId, $unitId);
    if (!empty($completed['available'])) {
        return [
            'available' => true,
            'complete' => in_array(1, $completed['attempts'], true),
        ];
    }
    $points = api_activity_unit_points_row($pdo, $studentId, $unitId);
    if (!empty($points['available'])
        && isset($points['columns']['unit_attempts'])
        && is_array($points['row'])) {
        return [
            'available' => true,
            'complete' => (int)($points['row']['unit_attempts'] ?? 0) >= 1,
        ];
    }
    return ['available' => false, 'complete' => false];
}

function api_activity_review_progress(PDO $pdo, int $studentId, int $unitId): array
{
    $points = api_activity_unit_points_row($pdo, $studentId, $unitId);
    if (empty($points['available'])
        || !isset($points['columns']['review_points'], $points['columns']['max_review_points'])) {
        return ['available' => false, 'percent' => null];
    }
    $row = is_array($points['row']) ? $points['row'] : [];
    $maximum = max(0.0, (float)($row['max_review_points'] ?? 0));
    if ($maximum <= 0.0) {
        return ['available' => true, 'percent' => 0.0];
    }
    $earned = max(0.0, (float)($row['review_points'] ?? 0));
    return [
        'available' => true,
        'percent' => max(0.0, min(100.0, ($earned / $maximum) * 100.0)),
    ];
}

function api_activity_unit_unlock_state(
    PDO $pdo,
    int $studentId,
    int $subjectVersionId,
    int $unitId,
): array {
    $previous = api_activity_previous_unit_id($pdo, $subjectVersionId, $unitId);
    if (empty($previous['available'])) {
        return [
            'available' => false,
            'unlocked' => false,
            'reason_code' => 'unit_unlock_state_unavailable',
            'reason' => 'تعذر التحقق من فتح الوحدة الآن.',
        ];
    }
    if ($previous['previous_unit_id'] === null) {
        return ['available' => true, 'unlocked' => true, 'reason_code' => '', 'reason' => ''];
    }

    $previousId = (int)$previous['previous_unit_id'];
    $attempt = api_activity_first_attempt_complete($pdo, $studentId, $previousId);
    $review = api_activity_review_progress($pdo, $studentId, $previousId);
    if (empty($attempt['available']) || empty($review['available'])) {
        return [
            'available' => false,
            'unlocked' => false,
            'reason_code' => 'unit_unlock_state_unavailable',
            'reason' => 'تعذر قراءة إنجاز الوحدة السابقة من المصدر المؤكد.',
        ];
    }
    if (empty($attempt['complete'])) {
        return [
            'available' => true,
            'unlocked' => false,
            'reason_code' => 'previous_unit_first_attempt_required',
            'reason' => 'أكمل المحاولة الأولى للوحدة السابقة لفتح هذه الوحدة.',
        ];
    }
    if ((float)$review['percent'] < API_ACTIVITY_REVIEW_UNLOCK_PERCENT) {
        return [
            'available' => true,
            'unlocked' => false,
            'reason_code' => 'previous_unit_review_required',
            'reason' => 'أكمل 30% من مراجعة الوحدة السابقة لفتح هذه الوحدة.',
        ];
    }
    return ['available' => true, 'unlocked' => true, 'reason_code' => '', 'reason' => ''];
}

function api_activity_lesson_unlock_state(PDO $pdo, int $studentId, int $unitId, int $lessonId): array
{
    $columns = api_activity_guard_table_columns($pdo, 'lessons');
    if (!isset($columns['id'], $columns['unit_id'])) {
        return [
            'available' => false,
            'unlocked' => false,
            'reason_code' => 'lesson_unlock_state_unavailable',
            'reason' => 'تعذر التحقق من فتح الدرس الآن.',
        ];
    }
    $orderColumn = null;
    foreach (['lesson_order', 'sort_order', 'position', 'id'] as $candidate) {
        if (isset($columns[$candidate])) {
            $orderColumn = $candidate;
            break;
        }
    }
    if ($orderColumn === null) {
        return [
            'available' => false,
            'unlocked' => false,
            'reason_code' => 'lesson_unlock_state_unavailable',
            'reason' => 'تعذر ترتيب دروس الوحدة.',
        ];
    }
    $current = $pdo->prepare("SELECT `{$orderColumn}` FROM lessons WHERE id=? AND unit_id=? LIMIT 1");
    $current->execute([$lessonId, $unitId]);
    $currentOrder = $current->fetchColumn();
    if ($currentOrder === false) {
        return [
            'available' => true,
            'unlocked' => false,
            'reason_code' => 'invalid_lesson',
            'reason' => 'الدرس المطلوب غير صالح.',
        ];
    }
    $previous = $pdo->prepare(
        "SELECT id FROM lessons WHERE unit_id=? "
        . "AND (`{$orderColumn}` < ? OR (`{$orderColumn}` = ? AND id < ?)) "
        . "ORDER BY `{$orderColumn}` DESC, id DESC LIMIT 1",
    );
    $previous->execute([$unitId, $currentOrder, $currentOrder, $lessonId]);
    $previousId = $previous->fetchColumn();
    if ($previousId === false) {
        return ['available' => true, 'unlocked' => true, 'reason_code' => '', 'reason' => ''];
    }

    foreach (['student_lesson_progress', 'lesson_progress'] as $table) {
        $progressColumns = api_activity_guard_table_columns($pdo, $table);
        if (!isset($progressColumns['student_id'], $progressColumns['lesson_id'])) {
            continue;
        }
        $progressColumn = null;
        foreach (['progress_percent', 'completion_percent', 'progress'] as $candidate) {
            if (isset($progressColumns[$candidate])) {
                $progressColumn = $candidate;
                break;
            }
        }
        if ($progressColumn === null) {
            continue;
        }
        $stmt = $pdo->prepare(
            "SELECT `{$progressColumn}` FROM `{$table}` WHERE student_id=? AND lesson_id=? LIMIT 1",
        );
        $stmt->execute([$studentId, (int)$previousId]);
        $percent = max(0.0, (float)($stmt->fetchColumn() ?: 0));
        return $percent >= 30.0
            ? ['available' => true, 'unlocked' => true, 'reason_code' => '', 'reason' => '']
            : [
                'available' => true,
                'unlocked' => false,
                'reason_code' => 'previous_lesson_progress_required',
                'reason' => 'أكمل 30% من الدرس السابق لفتح هذا الدرس.',
            ];
    }

    return [
        'available' => false,
        'unlocked' => false,
        'reason_code' => 'lesson_unlock_state_unavailable',
        'reason' => 'لم يتوفر مصدر مؤكد لتقدم الدرس السابق.',
    ];
}

function api_activity_policy_block(array $preview, string $code, string $reason): array
{
    $preview['eligibility'] = [
        'available' => false,
        'status' => 'unavailable',
        'reason' => $reason,
        'reason_code' => $code,
    ];
    return $preview;
}

function api_activity_apply_authoritative_policy(
    PDO $pdo,
    int $studentId,
    array $preview,
): array {
    $request = is_array($preview['_normalized_request'] ?? null)
        ? $preview['_normalized_request']
        : [];

    if (empty($preview['eligibility']['available'])) {
        return $preview;
    }
    if (!empty($preview['subscription']['blocking']) && empty($preview['subscription']['active'])) {
        return api_activity_policy_block(
            $preview,
            'subscription_required',
            'يجب تجديد الاشتراك قبل بدء هذا النشاط.',
        );
    }

    $guideSource = in_array(
        (string)($request['source'] ?? ''),
        ['home_guide', 'guide'],
        true,
    );
    if ($guideSource) {
        $expected = api_activity_authoritative_guide_action($pdo, $studentId, $request);
        if ($expected === null
            || !hash_equals((string)$expected['activity_type'], (string)($request['activity_type'] ?? ''))
            || !hash_equals((string)$expected['activity_mode'], (string)($request['activity_mode'] ?? ''))) {
            return api_activity_policy_block(
                $preview,
                'invalid_guide_action',
                'لم يعد نوع هذه الخطوة مطابقًا لخطة الموجّه الحالية.',
            );
        }
    }

    $unitId = (int)($request['unit_id'] ?? 0);
    $lessonId = (int)($request['lesson_id'] ?? 0);
    $nativeLessonRequest = (string)($request['activity_type'] ?? '') === 'lesson_practice'
        && (string)($request['activity_mode'] ?? '') === 'learn'
        && (string)($request['source'] ?? '') === 'lesson'
        && $unitId > 0
        && $lessonId > 0;

    // Native lesson requests were already checked against the configured
    // subject-version progress mode and threshold in api_activity_validate_lesson_access().
    // Keep the legacy fixed-30% guard only for older activity sources.
    if (!$nativeLessonRequest && $unitId > 0) {
        $unitState = api_activity_unit_unlock_state(
            $pdo,
            $studentId,
            (int)$request['subject_version_id'],
            $unitId,
        );
        if (empty($unitState['available']) || empty($unitState['unlocked'])) {
            return api_activity_policy_block(
                $preview,
                (string)$unitState['reason_code'],
                (string)$unitState['reason'],
            );
        }
    }

    if (!$nativeLessonRequest && $lessonId > 0) {
        if ($unitId <= 0) {
            return api_activity_policy_block($preview, 'invalid_lesson', 'تعذر التحقق من الدرس.');
        }
        $lessonState = api_activity_lesson_unlock_state($pdo, $studentId, $unitId, $lessonId);
        if (empty($lessonState['available']) || empty($lessonState['unlocked'])) {
            return api_activity_policy_block(
                $preview,
                (string)$lessonState['reason_code'],
                (string)$lessonState['reason'],
            );
        }
    }

    if (($request['activity_type'] ?? '') === 'unit_test') {
        if ($unitId <= 0) {
            return api_activity_policy_block($preview, 'unit_required', 'يجب تحديد وحدة الاختبار.');
        }
        $attempts = api_activity_unit_attempts($pdo, $studentId, $unitId);
        $preview['attempts'] = $attempts;
        if (empty($attempts['available'])) {
            return api_activity_policy_block(
                $preview,
                'attempt_state_unavailable',
                (string)$attempts['reason'],
            );
        }
        if ((int)$attempts['remaining'] <= 0) {
            return api_activity_policy_block(
                $preview,
                'attempt_limit_reached',
                'اكتملت المحاولات الست المتاحة لهذه الوحدة.',
            );
        }
    }

    $subjectLevelSmartReview = ($request['activity_type'] ?? '') === 'smart_review'
        && ($request['source'] ?? '') === 'review'
        && $unitId <= 0;
    if (in_array(($request['activity_type'] ?? ''), ['review', 'smart_review'], true)
        && !$subjectLevelSmartReview) {
        if ($unitId <= 0) {
            return api_activity_policy_block($preview, 'unit_required', 'يجب تحديد وحدة المراجعة.');
        }
        $attempt = api_activity_first_attempt_complete($pdo, $studentId, $unitId);
        if (empty($attempt['available'])) {
            return api_activity_policy_block(
                $preview,
                'attempt_state_unavailable',
                'تعذر التحقق من المحاولة الأولى للوحدة.',
            );
        }
        if (empty($attempt['complete'])) {
            return api_activity_policy_block(
                $preview,
                'review_not_ready',
                'أكمل المحاولة الأولى للوحدة قبل مراجعة الأخطاء.',
            );
        }
    }

    return $preview;
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

    $request = api_activity_normalize_request($payload);
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

        $keyHash = api_activity_idempotency_hash(api_activity_idempotency_key($rawKey));
        if (api_activity_table_exists($pdo, 'api_activity_sessions')) {
            $existing = api_activity_fetch_idempotency($pdo, $studentId, $keyHash, true);
            if ($existing) {
                if (!hash_equals((string)$existing['request_hash'], $requestHash)) {
                    api_activity_reject(
                        'idempotency_key_conflict',
                        'استُخدم مفتاح البدء لطلب مختلف.',
                        409,
                    );
                }
                $result = api_activity_response_from_row($existing, true);
                if ($ownsTransaction && $pdo->inTransaction()) {
                    $pdo->commit();
                }
                return $result;
            }

            $active = api_activity_find_active($pdo, $studentId, $requestHash);
            if ($active) {
                $result = api_activity_response_from_row($active, true);
                if ($ownsTransaction && $pdo->inTransaction()) {
                    $pdo->commit();
                }
                return $result;
            }
        }

        $preview = api_activity_apply_authoritative_policy(
            $pdo,
            $studentId,
            api_activity_preview($pdo, $session, $request),
        );
        if (empty($preview['eligibility']['available'])) {
            api_activity_reject(
                (string)($preview['eligibility']['reason_code'] ?: 'activity_unavailable'),
                (string)($preview['eligibility']['reason'] ?: 'النشاط غير متاح الآن.'),
                409,
            );
        }

        $result = api_activity_start($pdo, $session, $request, $rawKey);
        if ($ownsTransaction && $pdo->inTransaction()) {
            $pdo->commit();
        }
        return $result;
    } catch (ApiActivityRejected $error) {
        if ($ownsTransaction && $pdo->inTransaction()) {
            $pdo->rollBack();
        }
        api_error($error->apiCode, $error->getMessage(), $error->status);
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
