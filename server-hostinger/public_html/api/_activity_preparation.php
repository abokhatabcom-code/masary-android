<?php
declare(strict_types=1);

require_once __DIR__ . '/_student_training_center.php';

const API_ACTIVITY_TYPES = [
    'guide_step',
    'unit_test',
    'lesson_practice',
    'choose_test',
    'true_false_test',
    'connect_test',
    'fill_test',
    'review',
    'smart_review',
    'speed_test',
];
const API_ACTIVITY_MODES = ['learn', 'practice', 'review', 'test', 'speed'];
const API_ACTIVITY_SOURCES = ['home_guide', 'guide', 'subject', 'unit', 'lesson', 'review'];
const API_ACTIVITY_HEART_REQUIRED_TYPES = [
    'unit_test',
    'choose_test',
    'true_false_test',
    'connect_test',
    'fill_test',
    'review',
    'smart_review',
    'speed_test',
];

final class ApiActivityRejected extends RuntimeException
{
    public function __construct(
        public readonly string $apiCode,
        string $message,
        public readonly int $status,
    ) {
        parent::__construct($message);
    }
}

function api_activity_reject(string $code, string $message, int $status): never
{
    throw new ApiActivityRejected($code, $message, $status);
}

function api_activity_text(array $payload, string $key, int $maxLength): string
{
    $value = trim((string)($payload[$key] ?? ''));
    $length = function_exists('mb_strlen') ? mb_strlen($value, 'UTF-8') : strlen($value);
    if ($value === '' || $length > $maxLength) {
        api_error('validation_error', 'بيانات النشاط غير مكتملة أو غير صالحة.', 422);
    }
    return $value;
}

function api_activity_positive_id(array $payload, string $key, bool $required = false): ?int
{
    $raw = $payload[$key] ?? null;
    if ($raw === null || $raw === '') {
        if ($required) {
            api_error('validation_error', 'معرف المحتوى مطلوب.', 422);
        }
        return null;
    }
    if (filter_var($raw, FILTER_VALIDATE_INT) === false || (int)$raw <= 0) {
        api_error('validation_error', 'معرف المحتوى غير صالح.', 422);
    }
    return (int)$raw;
}

function api_activity_normalize_request(array $payload): array
{
    $request = [
        'subject_version_id' => api_activity_positive_id($payload, 'subject_version_id', true),
        'unit_id' => api_activity_positive_id($payload, 'unit_id'),
        'lesson_id' => api_activity_positive_id($payload, 'lesson_id'),
        'activity_type' => api_activity_text($payload, 'activity_type', 40),
        'activity_mode' => api_activity_text($payload, 'activity_mode', 30),
        'guide_step_id' => api_activity_positive_id($payload, 'guide_step_id'),
        'source' => api_activity_text($payload, 'source', 30),
    ];
    if (!in_array($request['activity_type'], API_ACTIVITY_TYPES, true)
        || !in_array($request['activity_mode'], API_ACTIVITY_MODES, true)
        || !in_array($request['source'], API_ACTIVITY_SOURCES, true)) {
        api_error('invalid_activity', 'نوع النشاط أو مصدره غير مسموح.', 422);
    }
    if ($request['lesson_id'] !== null && $request['unit_id'] === null) {
        api_error('validation_error', 'يجب تحديد الوحدة التابعة للدرس.', 422);
    }
    if (in_array($request['source'], ['home_guide', 'guide'], true)
        && $request['guide_step_id'] === null) {
        api_error('invalid_guide_step', 'خطوة الموجّه غير صالحة.', 422);
    }
    return $request;
}

function api_activity_request_hash(array $request): string
{
    ksort($request);
    return hash(
        'sha256',
        json_encode($request, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
    );
}

function api_activity_idempotency_key(string $value): string
{
    $value = trim($value);
    if (!preg_match('/^[A-Za-z0-9._:-]{16,128}$/', $value)) {
        api_error('invalid_idempotency_key', 'مفتاح منع التكرار غير صالح.', 422);
    }
    return $value;
}

function api_activity_idempotency_hash(string $value): string
{
    return hash_hmac('sha256', $value, api_server_secret());
}

function api_activity_table_exists(PDO $pdo, string $table): bool
{
    try {
        $driver = strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME));
        if ($driver === 'sqlite') {
            $stmt = $pdo->prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1");
            $stmt->execute([$table]);
            return $stmt->fetchColumn() !== false;
        }
        $stmt = $pdo->prepare('SHOW TABLES LIKE ?');
        $stmt->execute([$table]);
        return $stmt->fetchColumn() !== false;
    } catch (Throwable) {
        return false;
    }
}

function api_activity_row_label(array $row): string
{
    foreach (['title', 'name', 'unit_name', 'lesson_name', 'display_name'] as $key) {
        $value = trim((string)($row[$key] ?? ''));
        if ($value !== '') {
            return $value;
        }
    }
    return '';
}

function api_activity_subject(PDO $pdo, int $studentId, int $subjectVersionId): array
{
    $versionStmt = $pdo->prepare('SELECT * FROM subject_versions WHERE id=? LIMIT 1');
    $versionStmt->execute([$subjectVersionId]);
    $version = $versionStmt->fetch(PDO::FETCH_ASSOC);
    if (!$version) {
        api_error('invalid_content', 'المادة المطلوبة غير موجودة.', 422);
    }

    $subjectId = (int)($version['subject_id'] ?? 0);
    $subject = [];
    if ($subjectId > 0) {
        $subjectStmt = $pdo->prepare('SELECT * FROM subjects WHERE id=? LIMIT 1');
        $subjectStmt->execute([$subjectId]);
        $subject = $subjectStmt->fetch(PDO::FETCH_ASSOC) ?: [];
    }
    $subjectName = api_activity_row_label($subject);
    if ($subjectName === '') {
        $subjectName = api_activity_row_label($version);
    }

    $hearts = null;
    try {
        $state = $pdo->prepare(
            'SELECT hearts FROM student_subject_state WHERE student_id=? AND subject_version_id=? LIMIT 1',
        );
        $state->execute([$studentId, $subjectVersionId]);
        $value = $state->fetchColumn();
        if ($value !== false) {
            $hearts = max(0, (int)$value);
        }
    } catch (Throwable) {
        $hearts = null;
    }

    $assigned = $hearts !== null;
    if (!$assigned && function_exists('api_student_home_curriculum_subjects')) {
        try {
            foreach (api_student_home_curriculum_subjects($pdo, $studentId) as $candidate) {
                if ((int)($candidate['subject_version_id'] ?? 0) !== $subjectVersionId) {
                    continue;
                }
                $assigned = true;
                $hearts = max(0, (int)($candidate['hearts'] ?? 3));
                if ($subjectName === '') {
                    $subjectName = trim((string)($candidate['name'] ?? ''));
                }
                break;
            }
        } catch (Throwable) {
            $assigned = false;
        }
    }
    if (!$assigned) {
        api_error('content_not_assigned', 'هذه المادة غير مرتبطة بحساب الطالب.', 403);
    }

    return [
        'subject_version_id' => $subjectVersionId,
        'subject_name' => $subjectName !== '' ? $subjectName : 'المادة الدراسية',
        'hearts' => max(0, (int)($hearts ?? 0)),
    ];
}

function api_activity_unit(PDO $pdo, int $subjectVersionId, ?int $unitId): array
{
    if ($unitId === null) {
        return ['unit_id' => null, 'unit_title' => ''];
    }
    $stmt = $pdo->prepare('SELECT * FROM units WHERE id=? AND subject_version_id=? LIMIT 1');
    $stmt->execute([$unitId, $subjectVersionId]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) {
        api_error('invalid_content', 'الوحدة المطلوبة لا تتبع هذه المادة.', 422);
    }
    return ['unit_id' => $unitId, 'unit_title' => api_activity_row_label($row)];
}

function api_activity_lesson(PDO $pdo, ?int $unitId, ?int $lessonId): array
{
    if ($lessonId === null) {
        return ['lesson_id' => null, 'lesson_title' => ''];
    }
    if ($unitId === null) {
        api_error('invalid_content', 'تعذر التحقق من الدرس.', 422);
    }
    $stmt = $pdo->prepare('SELECT * FROM lessons WHERE id=? AND unit_id=? LIMIT 1');
    $stmt->execute([$lessonId, $unitId]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) {
        api_error('invalid_content', 'الدرس المطلوب لا يتبع هذه الوحدة.', 422);
    }
    return ['lesson_id' => $lessonId, 'lesson_title' => api_activity_row_label($row)];
}

function api_activity_validate_guide(PDO $pdo, int $studentId, array $request): void
{
    if (!in_array($request['source'], ['home_guide', 'guide'], true)) {
        return;
    }
    if (!function_exists('api_student_home_smart_guide')) {
        api_error('guide_unavailable', 'تعذر التحقق من خطوة الموجّه الآن.', 503);
    }
    $guide = api_student_home_smart_guide($pdo, $studentId);
    foreach ((array)($guide['steps'] ?? []) as $step) {
        if ((int)($step['id'] ?? 0) !== (int)$request['guide_step_id']) {
            continue;
        }
        $stepUnit = ((int)($step['unit_id'] ?? 0)) ?: null;
        $valid = (int)($step['subject_version_id'] ?? 0) === (int)$request['subject_version_id']
            && $stepUnit === $request['unit_id']
            && (string)($step['progress_state'] ?? 'pending') !== 'completed';
        if ($valid) {
            return;
        }
        break;
    }
    api_error('invalid_guide_step', 'خطوة الموجّه لم تعد صالحة للبدء.', 409);
}

function api_activity_training_tool(
    PDO $pdo,
    int $studentId,
    array $request,
): ?array {
    if (!in_array($request['source'], ['subject', 'review'], true)) {
        return null;
    }
    foreach (API_TRAINING_CENTER_TOOL_DEFINITIONS as $definition) {
        if ((string)($definition['activity_type'] ?? '') !== (string)$request['activity_type']) {
            continue;
        }
        $validContract = (string)($definition['activity_mode'] ?? '') === (string)$request['activity_mode']
            && (string)($definition['source'] ?? '') === (string)$request['source']
            && $request['unit_id'] === null
            && $request['lesson_id'] === null
            && $request['guide_step_id'] === null;
        if (!$validContract) {
            api_error('invalid_training_tool', 'عقد أداة التدريب غير صالح.', 422);
        }
        return api_training_center_tool_payload(
            $pdo,
            $studentId,
            (int)$request['subject_version_id'],
            $definition,
        );
    }
    return null;
}

function api_activity_balances(PDO $pdo, int $studentId, array $subject): array
{
    $gems = 0;
    if (function_exists('ik_dash_profile')) {
        try {
            $profile = (array)ik_dash_profile($pdo, $studentId);
            $gems = max(0, (int)($profile['gems'] ?? 0));
        } catch (Throwable) {
            $gems = 0;
        }
    }
    return ['hearts' => max(0, (int)$subject['hearts']), 'gems' => $gems];
}

function api_activity_subscription(PDO $pdo, int $studentId): array
{
    $status = 'غير متاح';
    $endsAt = '';
    if (function_exists('ik_dash_subscription')) {
        try {
            $value = (array)ik_dash_subscription($pdo, $studentId);
            $status = trim((string)($value['status'] ?? 'غير متاح'));
            $endsAt = trim((string)($value['ends_at'] ?? ''));
        } catch (Throwable) {
            $status = 'غير متاح';
        }
    }
    return [
        'available' => $status !== 'غير متاح',
        'active' => $status === 'نشط',
        'status' => $status,
        'ends_at' => $endsAt,
        'blocking' => false,
    ];
}

function api_activity_attempts(array $request): array
{
    if ($request['activity_type'] !== 'unit_test') {
        return [
            'available' => false,
            'used' => null,
            'remaining' => null,
            'maximum' => null,
            'reason' => 'لا ينطبق حد المحاولات على هذا النشاط.',
        ];
    }
    return [
        'available' => false,
        'used' => null,
        'remaining' => null,
        'maximum' => 6,
        'reason' => 'سيُربط سجل المحاولات المؤكد مع محرك الاختبارات في مرحلته المختصة.',
    ];
}

function api_activity_title(string $type): string
{
    return match ($type) {
        'unit_test' => 'اختبار الوحدة',
        'lesson_practice' => 'تدريب الدرس',
        'choose_test' => 'الاختيار من متعدد',
        'true_false_test' => 'صح أو خطأ',
        'connect_test' => 'التوصيل',
        'fill_test' => 'الإكمال',
        'review' => 'مراجعة الأخطاء',
        'smart_review' => 'مراجعة ذكية',
        'speed_test' => 'اختبار السرعة',
        default => 'خطوتك التعليمية التالية',
    };
}

function api_activity_find_active(PDO $pdo, int $studentId, string $requestHash): ?array
{
    if (!api_activity_table_exists($pdo, 'api_activity_sessions')) {
        return null;
    }
    $stmt = $pdo->prepare(
        "SELECT * FROM api_activity_sessions WHERE user_id=? AND request_hash=? "
        . "AND status IN ('created','in_progress') AND expires_at>? ORDER BY id DESC LIMIT 1",
    );
    $stmt->execute([$studentId, $requestHash, api_mysql_datetime(time())]);
    return $stmt->fetch(PDO::FETCH_ASSOC) ?: null;
}

function api_activity_preview(PDO $pdo, array $session, array $payload): array
{
    $studentId = (int)($session['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }
    $request = api_activity_normalize_request($payload);
    $subject = api_activity_subject($pdo, $studentId, (int)$request['subject_version_id']);
    $unit = api_activity_unit($pdo, (int)$request['subject_version_id'], $request['unit_id']);
    $lesson = api_activity_lesson($pdo, $request['unit_id'], $request['lesson_id']);
    api_activity_validate_guide($pdo, $studentId, $request);
    $trainingTool = api_activity_training_tool($pdo, $studentId, $request);

    $balances = api_activity_balances($pdo, $studentId, $subject);
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
    $requestHash = api_activity_request_hash($request);
    $active = api_activity_find_active($pdo, $studentId, $requestHash);
    $confirmedCount = $trainingTool === null ? null : ($trainingTool['item_count'] ?? null);

    return [
        'version' => hash(
            'sha256',
            $studentId . '|' . $requestHash . '|' . $balances['hearts'] . '|' . $balances['gems']
                . '|' . ($confirmedCount ?? -1),
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
}

function api_activity_public_preview(array $preview): array
{
    unset($preview['_normalized_request'], $preview['_request_hash']);
    return $preview;
}

function api_activity_uuid(): string
{
    $bytes = random_bytes(16);
    $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40);
    $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80);
    $hex = bin2hex($bytes);
    return substr($hex, 0, 8) . '-'
        . substr($hex, 8, 4) . '-'
        . substr($hex, 12, 4) . '-'
        . substr($hex, 16, 4) . '-'
        . substr($hex, 20);
}

function api_activity_fetch_idempotency(
    PDO $pdo,
    int $studentId,
    string $keyHash,
    bool $lock = false,
): ?array {
    $driver = strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME));
    $suffix = $lock && $driver === 'mysql' ? ' FOR UPDATE' : '';
    $stmt = $pdo->prepare(
        'SELECT * FROM api_activity_sessions WHERE user_id=? AND idempotency_key_hash=? LIMIT 1' . $suffix,
    );
    $stmt->execute([$studentId, $keyHash]);
    return $stmt->fetch(PDO::FETCH_ASSOC) ?: null;
}

function api_activity_response_from_row(array $row, bool $replayed): array
{
    $stored = json_decode((string)($row['response_json'] ?? ''), true);
    if (!is_array($stored)) {
        $stored = [
            'session_id' => (string)($row['public_session_id'] ?? ''),
            'status' => (string)($row['status'] ?? 'created'),
            'destination' => (string)($row['destination'] ?? 'activity_session_pending_ui'),
            'debit' => [
                'heart_debited' => max(0, (int)($row['heart_debited'] ?? 0)),
                'gems_debited' => max(0, (int)($row['gems_debited'] ?? 0)),
            ],
            'balances' => ['hearts' => 0, 'gems' => 0],
            'expires_at' => (string)($row['expires_at'] ?? ''),
        ];
    }
    $stored['replayed'] = $replayed;
    return $stored;
}

function api_activity_start(PDO $pdo, array $session, array $payload, string $rawKey): array
{
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
    $preview = api_activity_preview($pdo, $session, $request);
    if (empty($preview['eligibility']['available'])) {
        api_error(
            (string)($preview['eligibility']['reason_code'] ?: 'activity_unavailable'),
            (string)($preview['eligibility']['reason'] ?: 'النشاط غير متاح الآن.'),
            409,
        );
    }

    $ownsTransaction = !$pdo->inTransaction();
    if ($ownsTransaction) {
        $pdo->beginTransaction();
    }
    try {
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
            if ($ownsTransaction) {
                $pdo->commit();
            }
            return $result;
        }

        $subject = api_activity_subject($pdo, $studentId, (int)$request['subject_version_id']);
        $balances = api_activity_balances($pdo, $studentId, $subject);
        $requiredHearts = in_array($request['activity_type'], API_ACTIVITY_HEART_REQUIRED_TYPES, true) ? 1 : 0;
        if ($requiredHearts > 0 && $balances['hearts'] < $requiredHearts) {
            api_activity_reject(
                'insufficient_hearts',
                'لا توجد قلوب كافية لبدء هذا النشاط.',
                409,
            );
        }

        $active = api_activity_find_active($pdo, $studentId, $requestHash);
        if ($active) {
            $result = api_activity_response_from_row($active, true);
            if ($ownsTransaction) {
                $pdo->commit();
            }
            return $result;
        }

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
                'hearts' => max(0, (int)$balances['hearts']),
                'gems' => max(0, (int)$balances['gems']),
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
        if ($ownsTransaction) {
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
    }
}

function api_activity_start_status(PDO $pdo, array $session, string $rawKey): array
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
    return api_activity_response_from_row($row, true);
}
