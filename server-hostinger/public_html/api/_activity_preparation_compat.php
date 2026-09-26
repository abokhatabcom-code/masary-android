<?php
declare(strict_types=1);

function api_activity_compat_columns(PDO $pdo, string $table): array
{
    static $cache = [];
    $allowed = [
        'subject_versions',
        'units',
        'student_subject_hearts',
        'student_gems',
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

function api_activity_compat_subject_id(PDO $pdo, int $subjectVersionId): int
{
    $columns = api_activity_compat_columns($pdo, 'subject_versions');
    if (!isset($columns['id'], $columns['subject_id'])) {
        return 0;
    }
    $stmt = $pdo->prepare('SELECT subject_id FROM subject_versions WHERE id=? LIMIT 1');
    $stmt->execute([$subjectVersionId]);
    return max(0, (int)($stmt->fetchColumn() ?: 0));
}

function api_activity_subject_compatible(PDO $pdo, int $studentId, int $subjectVersionId): array
{
    $subject = api_activity_subject($pdo, $studentId, $subjectVersionId);
    $columns = api_activity_compat_columns($pdo, 'student_subject_hearts');
    if (!isset($columns['student_id'], $columns['subject_id'], $columns['hearts'])) {
        return $subject;
    }
    $subjectId = api_activity_compat_subject_id($pdo, $subjectVersionId);
    if ($subjectId <= 0) {
        return $subject;
    }
    $stmt = $pdo->prepare(
        'SELECT hearts FROM student_subject_hearts WHERE student_id=? AND subject_id=? LIMIT 1',
    );
    $stmt->execute([$studentId, $subjectId]);
    $hearts = $stmt->fetchColumn();
    if ($hearts !== false) {
        $subject['hearts'] = max(0, (int)$hearts);
    }
    return $subject;
}

function api_activity_unit_compatible(PDO $pdo, int $subjectVersionId, ?int $unitId): array
{
    if ($unitId === null) {
        return ['unit_id' => null, 'unit_title' => ''];
    }
    $columns = api_activity_compat_columns($pdo, 'units');
    if (isset($columns['subject_version_id'])) {
        $stmt = $pdo->prepare('SELECT * FROM units WHERE id=? AND subject_version_id=? LIMIT 1');
        $stmt->execute([$unitId, $subjectVersionId]);
    } elseif (isset($columns['subject_id'])) {
        $subjectId = api_activity_compat_subject_id($pdo, $subjectVersionId);
        if ($subjectId <= 0) {
            api_error('invalid_content', 'تعذر ربط الوحدة بالمادة.', 422);
        }
        $stmt = $pdo->prepare('SELECT * FROM units WHERE id=? AND subject_id=? LIMIT 1');
        $stmt->execute([$unitId, $subjectId]);
    } else {
        api_error('activity_schema_unavailable', 'تعذر التحقق من بنية الوحدات الحالية.', 503);
    }
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) {
        api_error('invalid_content', 'الوحدة المطلوبة لا تتبع هذه المادة.', 422);
    }
    return ['unit_id' => $unitId, 'unit_title' => api_activity_row_label($row)];
}

function api_activity_balances_compatible(PDO $pdo, int $studentId, array $subject): array
{
    $balances = api_activity_balances($pdo, $studentId, $subject);
    $columns = api_activity_compat_columns($pdo, 'student_gems');
    if (!isset($columns['student_id'], $columns['gems'])) {
        return $balances;
    }
    $stmt = $pdo->prepare('SELECT gems FROM student_gems WHERE student_id=? LIMIT 1');
    $stmt->execute([$studentId]);
    $gems = $stmt->fetchColumn();
    if ($gems !== false) {
        $balances['gems'] = max(0, (int)$gems);
    }
    return $balances;
}

function api_activity_preview_compatible(PDO $pdo, array $session, array $payload): array
{
    $studentId = (int)($session['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }
    $request = api_activity_normalize_request($payload);
    $subject = api_activity_subject_compatible(
        $pdo,
        $studentId,
        (int)$request['subject_version_id'],
    );
    $unit = api_activity_unit_compatible(
        $pdo,
        (int)$request['subject_version_id'],
        $request['unit_id'],
    );
    $lesson = api_activity_lesson($pdo, $request['unit_id'], $request['lesson_id']);
    api_activity_validate_lesson_access($pdo, $studentId, $request);
    api_activity_validate_guide($pdo, $studentId, $request);
    $trainingTool = api_activity_training_tool($pdo, $studentId, $request);

    $balances = api_activity_balances_compatible($pdo, $studentId, $subject);
    $requiredHearts = in_array($request['activity_type'], API_ACTIVITY_HEART_REQUIRED_TYPES, true) ? 1 : 0;
    $toolAvailable = $trainingTool === null || !empty($trainingTool['available']);
    $heartsAvailable = $requiredHearts === 0 || $balances['hearts'] >= $requiredHearts;
    $available = $toolAvailable && $heartsAvailable;
    if (!$toolAvailable) {
        $reasonCode = (string)($trainingTool['status'] ?? '') === 'source_unavailable'
            ? 'training_source_unavailable'
            : 'training_empty';
        $reason = trim((string)($trainingTool['reason'] ?? ''));
        if ($reason === '') {
            $reason = 'أداة التدريب غير متاحة الآن.';
        }
    } elseif (!$heartsAvailable) {
        $reasonCode = 'insufficient_hearts';
        $reason = 'لا توجد قلوب كافية لبدء هذا النشاط.';
    } else {
        $reasonCode = '';
        $reason = '';
    }
    $confirmedCount = $trainingTool !== null && isset($trainingTool['item_count'])
        ? max(0, (int)$trainingTool['item_count'])
        : null;
    $requestHash = api_activity_request_hash($request);
    $active = api_activity_find_active($pdo, $studentId, $requestHash);

    $preview = [
        'version' => hash(
            'sha256',
            $studentId . '|' . $requestHash . '|' . $balances['hearts'] . '|' . $balances['gems'],
        ),
        'generated_at' => gmdate(DATE_ATOM),
        'activity' => array_merge($subject, $unit, $lesson, [
            'activity_type' => $request['activity_type'],
            'activity_mode' => $request['activity_mode'],
            'title' => api_activity_title($request['activity_type']),
            'estimated_minutes' => null,
            'question_count' => $confirmedCount,
        ]),
        'eligibility' => [
            'available' => $available,
            'status' => $available ? 'ready' : 'unavailable',
            'reason' => $reason,
            'reason_code' => $reasonCode,
        ],
        'subscription' => api_activity_subscription($pdo, $studentId),
        'balances' => $balances,
        'cost' => [
            'required_hearts' => $requiredHearts,
            'heart_cost' => 0,
            'gem_cost' => 0,
        ],
        'attempts' => api_activity_attempts($request),
        'resume' => $active ? [
            'available' => true,
            'session_id' => (string)$active['public_session_id'],
            'status' => (string)$active['status'],
            'expires_at' => (string)$active['expires_at'],
            'reason' => 'يمكن استئناف الجلسة الحالية دون إنشاء جلسة أو خصم جديد.',
        ] : [
            'available' => false,
            'session_id' => null,
            'status' => '',
            'expires_at' => '',
            'reason' => '',
        ],
        '_normalized_request' => $request,
        '_request_hash' => $requestHash,
    ];

    return api_activity_apply_authoritative_policy($pdo, $studentId, $preview);
}

function api_activity_start_compatible_guarded(
    PDO $pdo,
    array $session,
    array $payload,
    string $rawKey,
): array {
    $studentId = (int)($session['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }
    if (!api_activity_table_exists($pdo, 'api_activity_sessions')) {
        api_error('activity_schema_missing', 'خدمة جلسات النشاط لم تُجهّز في هذه البيئة بعد.', 503);
    }

    $request = api_activity_normalize_request($payload);
    $requestHash = api_activity_request_hash($request);
    $keyHash = api_activity_idempotency_hash(api_activity_idempotency_key($rawKey));
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

        $preview = api_activity_preview_compatible($pdo, $session, $request);
        if (empty($preview['eligibility']['available'])) {
            api_activity_reject(
                (string)($preview['eligibility']['reason_code'] ?: 'activity_unavailable'),
                (string)($preview['eligibility']['reason'] ?: 'النشاط غير متاح الآن.'),
                409,
            );
        }

        $balances = (array)$preview['balances'];
        $publicId = api_activity_uuid();
        $now = api_mysql_datetime(time());
        $expiresAt = api_mysql_datetime(time() + 7200);
        $result = [
            'session_id' => $publicId,
            'status' => 'created',
            'destination' => 'activity_session_pending_ui',
            'replayed' => false,
            'debit' => ['heart_debited' => 0, 'gems_debited' => 0],
            'balances' => [
                'hearts' => max(0, (int)($balances['hearts'] ?? 0)),
                'gems' => max(0, (int)($balances['gems'] ?? 0)),
            ],
            'expires_at' => $expiresAt,
        ];
        $stmt = $pdo->prepare(
            'INSERT INTO api_activity_sessions '
            . '(public_session_id,user_id,subject_version_id,unit_id,lesson_id,activity_type,activity_mode,'
            . 'source,guide_step_id,status,idempotency_key_hash,request_hash,request_json,response_json,'
            . 'destination,heart_debited,gems_debited,expires_at,created_at,updated_at) '
            . 'VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
        );
        $stmt->execute([
            $publicId,
            $studentId,
            $request['subject_version_id'],
            $request['unit_id'],
            $request['lesson_id'],
            $request['activity_type'],
            $request['activity_mode'],
            $request['source'],
            $request['guide_step_id'],
            'created',
            $keyHash,
            $requestHash,
            json_encode($request, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
            json_encode($result, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
            'activity_session_pending_ui',
            0,
            0,
            $expiresAt,
            $now,
            $now,
        ]);

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
