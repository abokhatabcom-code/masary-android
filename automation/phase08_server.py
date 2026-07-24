#!/usr/bin/env python3
from pathlib import Path
from textwrap import dedent
import json

ROOT = Path(__file__).resolve().parents[1]


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(dedent(content).lstrip(), encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:120]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


write(
    "server-hostinger/public_html/api/_activity_preparation.php",
    r'''
    <?php
    declare(strict_types=1);

    const API_ACTIVITY_TYPES = ['guide_step','unit_test','lesson_practice','review','smart_review','speed_test'];
    const API_ACTIVITY_MODES = ['learn','practice','review','test','speed'];
    const API_ACTIVITY_SOURCES = ['home_guide','guide','subject','unit','lesson','review'];
    const API_ACTIVITY_HEART_REQUIRED_TYPES = ['unit_test','review','smart_review','speed_test'];

    function api_activity_value(array $payload, string $key, int $maxLength): string
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
            if ($required) api_error('validation_error', 'معرف المحتوى مطلوب.', 422);
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
            'activity_type' => api_activity_value($payload, 'activity_type', 40),
            'activity_mode' => api_activity_value($payload, 'activity_mode', 30),
            'guide_step_id' => api_activity_positive_id($payload, 'guide_step_id'),
            'source' => api_activity_value($payload, 'source', 30),
        ];
        if (!in_array($request['activity_type'], API_ACTIVITY_TYPES, true)
            || !in_array($request['activity_mode'], API_ACTIVITY_MODES, true)
            || !in_array($request['source'], API_ACTIVITY_SOURCES, true)) {
            api_error('invalid_activity', 'نوع النشاط أو مصدره غير مسموح.', 422);
        }
        if ($request['lesson_id'] !== null && $request['unit_id'] === null) {
            api_error('validation_error', 'يجب تحديد الوحدة التابعة للدرس.', 422);
        }
        if (in_array($request['source'], ['home_guide','guide'], true) && $request['guide_step_id'] === null) {
            api_error('invalid_guide_step', 'خطوة الموجّه غير صالحة.', 422);
        }
        return $request;
    }

    function api_activity_request_hash(array $request): string
    {
        ksort($request);
        return hash('sha256', json_encode($request, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR));
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

    function api_activity_subject(PDO $pdo, int $studentId, int $subjectVersionId): array
    {
        $stmt = $pdo->prepare(
            'SELECT sv.id AS subject_version_id,s.name AS subject_name '
            . 'FROM subject_versions sv JOIN subjects s ON s.id=sv.subject_id WHERE sv.id=? LIMIT 1'
        );
        $stmt->execute([$subjectVersionId]);
        $subject = $stmt->fetch(PDO::FETCH_ASSOC);
        if (!$subject) api_error('invalid_content', 'المادة المطلوبة غير موجودة.', 422);

        $hearts = null;
        try {
            $state = $pdo->prepare('SELECT hearts FROM student_subject_state WHERE student_id=? AND subject_version_id=? LIMIT 1');
            $state->execute([$studentId, $subjectVersionId]);
            $value = $state->fetchColumn();
            if ($value !== false) $hearts = max(0, (int)$value);
        } catch (Throwable) {
            $hearts = null;
        }

        $assigned = $hearts !== null;
        if (!$assigned && function_exists('api_student_home_curriculum_subjects')) {
            try {
                foreach (api_student_home_curriculum_subjects($pdo, $studentId) as $candidate) {
                    if ((int)($candidate['subject_version_id'] ?? 0) === $subjectVersionId) {
                        $assigned = true;
                        $hearts = max(0, (int)($candidate['hearts'] ?? 3));
                        break;
                    }
                }
            } catch (Throwable) {
                $assigned = false;
            }
        }
        if (!$assigned) api_error('content_not_assigned', 'هذه المادة غير مرتبطة بحساب الطالب.', 403);
        return [
            'subject_version_id' => $subjectVersionId,
            'subject_name' => trim((string)($subject['subject_name'] ?? '')),
            'hearts' => max(0, (int)($hearts ?? 0)),
        ];
    }

    function api_activity_unit(PDO $pdo, int $subjectVersionId, ?int $unitId): array
    {
        if ($unitId === null) return ['unit_id' => null, 'unit_title' => ''];
        $stmt = $pdo->prepare('SELECT id,title FROM units WHERE id=? AND subject_version_id=? LIMIT 1');
        $stmt->execute([$unitId, $subjectVersionId]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC);
        if (!$row) api_error('invalid_content', 'الوحدة المطلوبة لا تتبع هذه المادة.', 422);
        return ['unit_id' => (int)$row['id'], 'unit_title' => trim((string)($row['title'] ?? ''))];
    }

    function api_activity_lesson(PDO $pdo, ?int $unitId, ?int $lessonId): array
    {
        if ($lessonId === null) return ['lesson_id' => null, 'lesson_title' => ''];
        if ($unitId === null) api_error('invalid_content', 'تعذر التحقق من الدرس.', 422);
        $stmt = $pdo->prepare('SELECT id,title FROM lessons WHERE id=? AND unit_id=? LIMIT 1');
        $stmt->execute([$lessonId, $unitId]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC);
        if (!$row) api_error('invalid_content', 'الدرس المطلوب لا يتبع هذه الوحدة.', 422);
        return ['lesson_id' => (int)$row['id'], 'lesson_title' => trim((string)($row['title'] ?? ''))];
    }

    function api_activity_validate_guide(PDO $pdo, int $studentId, array $request): void
    {
        if (!in_array($request['source'], ['home_guide','guide'], true)) return;
        if (!function_exists('api_student_home_smart_guide')) {
            api_error('guide_unavailable', 'تعذر التحقق من خطوة الموجّه الآن.', 503);
        }
        $guide = api_student_home_smart_guide($pdo, $studentId);
        foreach ((array)($guide['steps'] ?? []) as $step) {
            if ((int)($step['id'] ?? 0) !== (int)$request['guide_step_id']) continue;
            $sameSubject = (int)($step['subject_version_id'] ?? 0) === (int)$request['subject_version_id'];
            $stepUnit = ((int)($step['unit_id'] ?? 0)) ?: null;
            if ($sameSubject && $stepUnit === $request['unit_id'] && (string)($step['progress_state'] ?? 'pending') !== 'completed') return;
            break;
        }
        api_error('invalid_guide_step', 'خطوة الموجّه لم تعد صالحة للبدء.', 409);
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
            // المرحلة الحالية لا تفرض اشتراكًا جديدًا فوق قواعد المنصة القائمة.
            'blocking' => false,
        ];
    }

    function api_activity_attempts(array $request): array
    {
        if ($request['activity_type'] !== 'unit_test') {
            return ['available' => false, 'used' => null, 'remaining' => null, 'maximum' => null, 'reason' => 'لا ينطبق حد المحاولات على هذا النشاط.'];
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
            'review' => 'مراجعة الأخطاء',
            'smart_review' => 'مراجعة ذكية',
            'speed_test' => 'اختبار السرعة',
            default => 'خطوتك التعليمية التالية',
        };
    }

    function api_activity_find_active(PDO $pdo, int $studentId, string $requestHash): ?array
    {
        if (!api_activity_table_exists($pdo, 'api_activity_sessions')) return null;
        $stmt = $pdo->prepare(
            "SELECT * FROM api_activity_sessions WHERE user_id=? AND request_hash=? "
            . "AND status IN ('created','in_progress') AND expires_at>? ORDER BY id DESC LIMIT 1"
        );
        $stmt->execute([$studentId, $requestHash, api_mysql_datetime(time())]);
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

    function api_activity_preview(PDO $pdo, array $session, array $payload): array
    {
        $studentId = (int)($session['user_id'] ?? 0);
        if ($studentId <= 0) api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
        $request = api_activity_normalize_request($payload);
        $subject = api_activity_subject($pdo, $studentId, (int)$request['subject_version_id']);
        $unit = api_activity_unit($pdo, (int)$request['subject_version_id'], $request['unit_id']);
        $lesson = api_activity_lesson($pdo, $request['unit_id'], $request['lesson_id']);
        api_activity_validate_guide($pdo, $studentId, $request);

        $balances = api_activity_balances($pdo, $studentId, $subject);
        $requiredHearts = in_array($request['activity_type'], API_ACTIVITY_HEART_REQUIRED_TYPES, true) ? 1 : 0;
        $available = $requiredHearts === 0 || $balances['hearts'] >= $requiredHearts;
        $reasonCode = $available ? '' : 'insufficient_hearts';
        $reason = $available ? '' : 'لا توجد قلوب كافية لبدء هذا النشاط.';
        $requestHash = api_activity_request_hash($request);
        $active = api_activity_find_active($pdo, $studentId, $requestHash);

        return [
            'version' => hash('sha256', $studentId . '|' . $requestHash . '|' . $balances['hearts'] . '|' . $balances['gems']),
            'generated_at' => gmdate(DATE_ATOM),
            'activity' => array_merge($subject, $unit, $lesson, [
                'activity_type' => $request['activity_type'],
                'activity_mode' => $request['activity_mode'],
                'title' => api_activity_title($request['activity_type']),
                'estimated_minutes' => null,
                'question_count' => null,
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
                // لا يُخصم قلب عند إنشاء الجلسة حتى يثبت محرك الاختبارات سياسة الخصم النهائية.
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
        return substr($hex,0,8).'-'.substr($hex,8,4).'-'.substr($hex,12,4).'-'.substr($hex,16,4).'-'.substr($hex,20);
    }

    function api_activity_fetch_idempotency(PDO $pdo, int $studentId, string $keyHash, bool $lock = false): ?array
    {
        $suffix = $lock && strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME)) === 'mysql' ? ' FOR UPDATE' : '';
        $stmt = $pdo->prepare('SELECT * FROM api_activity_sessions WHERE user_id=? AND idempotency_key_hash=? LIMIT 1' . $suffix);
        $stmt->execute([$studentId, $keyHash]);
        return $stmt->fetch(PDO::FETCH_ASSOC) ?: null;
    }

    function api_activity_start(PDO $pdo, array $session, array $payload, string $rawKey): array
    {
        $studentId = (int)($session['user_id'] ?? 0);
        if ($studentId <= 0) api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
        if (!api_activity_table_exists($pdo, 'api_activity_sessions')) {
            api_error('activity_schema_missing', 'خدمة جلسات النشاط لم تُجهّز في هذه البيئة بعد.', 503);
        }
        $request = api_activity_normalize_request($payload);
        $requestHash = api_activity_request_hash($request);
        $keyHash = api_activity_idempotency_hash(api_activity_idempotency_key($rawKey));
        $ownsTransaction = !$pdo->inTransaction();
        if ($ownsTransaction) $pdo->beginTransaction();
        try {
            $existing = api_activity_fetch_idempotency($pdo, $studentId, $keyHash, true);
            if ($existing) {
                if (!hash_equals((string)$existing['request_hash'], $requestHash)) {
                    api_error('idempotency_key_conflict', 'استُخدم مفتاح البدء لطلب مختلف.', 409);
                }
                $result = api_activity_response_from_row($existing, true);
                if ($ownsTransaction && $pdo->inTransaction()) $pdo->commit();
                return $result;
            }

            $preview = api_activity_preview($pdo, $session, $request);
            if (empty($preview['eligibility']['available'])) {
                api_error(
                    (string)($preview['eligibility']['reason_code'] ?: 'activity_unavailable'),
                    (string)($preview['eligibility']['reason'] ?: 'النشاط غير متاح الآن.'),
                    409,
                );
            }

            $active = api_activity_find_active($pdo, $studentId, $requestHash);
            if ($active) {
                $result = api_activity_response_from_row($active, true);
                if ($ownsTransaction && $pdo->inTransaction()) $pdo->commit();
                return $result;
            }

            $publicId = api_activity_uuid();
            $now = api_mysql_datetime(time());
            $expiresAt = api_mysql_datetime(time() + 7200);
            $balances = (array)$preview['balances'];
            $result = [
                'session_id' => $publicId,
                'status' => 'created',
                'destination' => 'activity_session_pending_ui',
                'replayed' => false,
                'debit' => ['heart_debited' => 0, 'gems_debited' => 0],
                'balances' => ['hearts' => max(0,(int)$balances['hearts']), 'gems' => max(0,(int)$balances['gems'])],
                'expires_at' => $expiresAt,
            ];
            $stmt = $pdo->prepare(
                'INSERT INTO api_activity_sessions '
                . '(public_session_id,user_id,subject_version_id,unit_id,lesson_id,activity_type,activity_mode,source,guide_step_id,status,idempotency_key_hash,request_hash,request_json,response_json,destination,heart_debited,gems_debited,expires_at,created_at,updated_at) '
                . 'VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)'
            );
            $stmt->execute([
                $publicId,$studentId,$request['subject_version_id'],$request['unit_id'],$request['lesson_id'],
                $request['activity_type'],$request['activity_mode'],$request['source'],$request['guide_step_id'],'created',
                $keyHash,$requestHash,json_encode($request,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES),
                json_encode($result,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES),'activity_session_pending_ui',0,0,$expiresAt,$now,$now,
            ]);
            if ($ownsTransaction) $pdo->commit();
            return $result;
        } catch (Throwable $error) {
            if ($ownsTransaction && $pdo->inTransaction()) $pdo->rollBack();
            throw $error;
        }
    }

    function api_activity_start_status(PDO $pdo, array $session, string $rawKey): array
    {
        $studentId = (int)($session['user_id'] ?? 0);
        if ($studentId <= 0) api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
        if (!api_activity_table_exists($pdo, 'api_activity_sessions')) {
            api_error('activity_start_not_found', 'لا توجد محاولة بدء مسجلة.', 404);
        }
        $row = api_activity_fetch_idempotency(
            $pdo,
            $studentId,
            api_activity_idempotency_hash(api_activity_idempotency_key($rawKey)),
        );
        if (!$row) api_error('activity_start_not_found', 'لا توجد محاولة بدء مسجلة.', 404);
        return api_activity_response_from_row($row, true);
    }
    ''',
)

write(
    "server-hostinger/public_html/api/v1/student/activity/preview.php",
    r'''
    <?php
    declare(strict_types=1);
    require_once dirname(__DIR__, 3) . '/_tokens.php';
    require_once dirname(__DIR__, 3) . '/_student_home.php';
    require_once dirname(__DIR__, 3) . '/_student_home_subjects.php';
    require_once dirname(__DIR__, 3) . '/_activity_preparation.php';
    api_require_method('POST');
    $pdo = api_db();
    $session = api_authenticate_access_token($pdo, api_bearer_token());
    api_rate_limit('activity_preview', (string)($session['user_id'] ?? '0'), 90, 60);
    api_response(true, api_activity_public_preview(api_activity_preview($pdo, $session, api_read_json())));
    ''',
)

write(
    "server-hostinger/public_html/api/v1/student/activity/start.php",
    r'''
    <?php
    declare(strict_types=1);
    require_once dirname(__DIR__, 3) . '/_tokens.php';
    require_once dirname(__DIR__, 3) . '/_student_home.php';
    require_once dirname(__DIR__, 3) . '/_student_home_subjects.php';
    require_once dirname(__DIR__, 3) . '/_activity_preparation.php';
    api_require_method('POST');
    $pdo = api_db();
    $session = api_authenticate_access_token($pdo, api_bearer_token());
    api_rate_limit('activity_start', (string)($session['user_id'] ?? '0'), 30, 60);
    api_response(true, api_activity_start($pdo, $session, api_read_json(), (string)($_SERVER['HTTP_IDEMPOTENCY_KEY'] ?? '')));
    ''',
)

write(
    "server-hostinger/public_html/api/v1/student/activity/start-status.php",
    r'''
    <?php
    declare(strict_types=1);
    require_once dirname(__DIR__, 3) . '/_tokens.php';
    require_once dirname(__DIR__, 3) . '/_activity_preparation.php';
    api_require_method('GET');
    $pdo = api_db();
    $session = api_authenticate_access_token($pdo, api_bearer_token());
    api_rate_limit('activity_start_status', (string)($session['user_id'] ?? '0'), 60, 60);
    api_response(true, api_activity_start_status($pdo, $session, (string)($_GET['idempotency_key'] ?? '')));
    ''',
)

write(
    "server-hostinger/database/20260724_api_activity_sessions.sql",
    r'''
    -- Phase 08 review-only migration. Do not execute automatically on production.
    CREATE TABLE IF NOT EXISTS `api_activity_sessions` (
      `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
      `public_session_id` CHAR(36) NOT NULL,
      `user_id` BIGINT UNSIGNED NOT NULL,
      `subject_version_id` BIGINT UNSIGNED NOT NULL,
      `unit_id` BIGINT UNSIGNED NULL,
      `lesson_id` BIGINT UNSIGNED NULL,
      `activity_type` VARCHAR(40) NOT NULL,
      `activity_mode` VARCHAR(30) NOT NULL,
      `source` VARCHAR(30) NOT NULL,
      `guide_step_id` BIGINT UNSIGNED NULL,
      `status` VARCHAR(24) NOT NULL DEFAULT 'created',
      `idempotency_key_hash` CHAR(64) NOT NULL,
      `request_hash` CHAR(64) NOT NULL,
      `request_json` LONGTEXT NOT NULL,
      `response_json` LONGTEXT NOT NULL,
      `destination` VARCHAR(80) NOT NULL,
      `heart_debited` INT UNSIGNED NOT NULL DEFAULT 0,
      `gems_debited` INT UNSIGNED NOT NULL DEFAULT 0,
      `expires_at` DATETIME NOT NULL,
      `started_at` DATETIME NULL,
      `completed_at` DATETIME NULL,
      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (`id`),
      UNIQUE KEY `uq_activity_user_idempotency` (`user_id`,`idempotency_key_hash`),
      KEY `idx_activity_user_request_active` (`user_id`,`request_hash`,`status`,`expires_at`),
      KEY `idx_activity_public_session` (`public_session_id`),
      KEY `idx_activity_expiry` (`status`,`expires_at`),
      CONSTRAINT `fk_activity_session_user` FOREIGN KEY (`user_id`) REFERENCES `app_users` (`id`) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    ''',
)

write(
    "server-hostinger/tests/activity_preparation_behavior_test.php",
    r'''
    <?php
    declare(strict_types=1);
    final class ActivityApiTestError extends RuntimeException {
        function __construct(public string $apiCode, public int $status) { parent::__construct($apiCode); }
    }
    function api_error(string $code,string $message,int $status=400):never { throw new ActivityApiTestError($code,$status); }
    function api_mysql_datetime(int $timestamp):string { return gmdate('Y-m-d H:i:s',$timestamp); }
    function api_server_secret():string { return str_repeat('activity-test-secret-',3); }
    function api_student_home_smart_guide(PDO $pdo,int $studentId):array {
        return ['steps'=>[['id'=>91,'subject_version_id'=>12,'unit_id'=>4,'progress_state'=>'pending']]];
    }
    function ik_dash_profile(PDO $pdo,int $studentId):array { return ['gems'=>10]; }
    function ik_dash_subscription(PDO $pdo,int $studentId):array { return ['status'=>'نشط','ends_at'=>'2027-01-01']; }
    require dirname(__DIR__).'/public_html/api/_activity_preparation.php';
    function check(bool $value,string $message):void { if(!$value) throw new RuntimeException($message); }

    $pdo=new PDO('sqlite::memory:');
    $pdo->setAttribute(PDO::ATTR_ERRMODE,PDO::ERRMODE_EXCEPTION);
    $pdo->exec('CREATE TABLE subjects(id INTEGER PRIMARY KEY,name TEXT NOT NULL)');
    $pdo->exec('CREATE TABLE subject_versions(id INTEGER PRIMARY KEY,subject_id INTEGER NOT NULL)');
    $pdo->exec('CREATE TABLE units(id INTEGER PRIMARY KEY,subject_version_id INTEGER NOT NULL,title TEXT NOT NULL)');
    $pdo->exec('CREATE TABLE lessons(id INTEGER PRIMARY KEY,unit_id INTEGER NOT NULL,title TEXT NOT NULL)');
    $pdo->exec('CREATE TABLE student_subject_state(student_id INTEGER NOT NULL,subject_version_id INTEGER NOT NULL,hearts INTEGER NOT NULL)');
    $pdo->exec("CREATE TABLE api_activity_sessions(
      id INTEGER PRIMARY KEY AUTOINCREMENT,public_session_id TEXT NOT NULL,user_id INTEGER NOT NULL,subject_version_id INTEGER NOT NULL,
      unit_id INTEGER,lesson_id INTEGER,activity_type TEXT NOT NULL,activity_mode TEXT NOT NULL,source TEXT NOT NULL,guide_step_id INTEGER,
      status TEXT NOT NULL,idempotency_key_hash TEXT NOT NULL,request_hash TEXT NOT NULL,request_json TEXT NOT NULL,response_json TEXT NOT NULL,
      destination TEXT NOT NULL,heart_debited INTEGER NOT NULL,gems_debited INTEGER NOT NULL,expires_at TEXT NOT NULL,started_at TEXT,completed_at TEXT,
      created_at TEXT NOT NULL,updated_at TEXT NOT NULL,UNIQUE(user_id,idempotency_key_hash))");
    $pdo->exec("INSERT INTO subjects VALUES(2,'الرياضيات')");
    $pdo->exec('INSERT INTO subject_versions VALUES(12,2)');
    $pdo->exec("INSERT INTO units VALUES(4,12,'الوحدة الأولى')");
    $pdo->exec('INSERT INTO student_subject_state VALUES(42,12,3)');
    $session=['user_id'=>42];
    $payload=['subject_version_id'=>12,'unit_id'=>4,'activity_type'=>'guide_step','activity_mode'=>'learn','guide_step_id'=>91,'source'=>'home_guide'];

    $before=(int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn();
    $preview=api_activity_preview($pdo,$session,$payload);
    check($preview['eligibility']['available']===true,'eligible activity rejected');
    check((int)$preview['balances']['hearts']===3,'trusted hearts not loaded');
    check((int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn()===$before,'preview mutated sessions');

    $key='phase08-test-key-0000000001';
    $first=api_activity_start($pdo,$session,$payload,$key);
    $second=api_activity_start($pdo,$session,$payload,$key);
    check($first['session_id']===$second['session_id'],'idempotent replay created another session');
    check($second['replayed']===true,'replayed flag missing');
    check((int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn()===1,'duplicate session stored');
    check($first['debit']['heart_debited']===0&&$first['debit']['gems_debited']===0,'unapproved debit occurred');
    $status=api_activity_start_status($pdo,$session,$key);
    check($status['session_id']===$first['session_id'],'status lookup mismatch');

    try {
        api_activity_start($pdo,$session,array_replace($payload,['activity_mode'=>'practice']),$key);
        throw new RuntimeException('expected idempotency conflict');
    } catch(ActivityApiTestError $error) {
        check($error->apiCode==='idempotency_key_conflict'&&$error->status===409,'wrong conflict result');
    }

    $pdo->exec('UPDATE student_subject_state SET hearts=0 WHERE student_id=42 AND subject_version_id=12');
    $blocked=api_activity_preview($pdo,$session,array_replace($payload,['activity_type'=>'review','activity_mode'=>'review','source'=>'review','guide_step_id'=>null]));
    check($blocked['eligibility']['available']===false,'zero hearts review was allowed');
    check($blocked['eligibility']['reason_code']==='insufficient_hearts','zero hearts reason missing');
    echo "Activity preparation behavioral tests passed.\n";
    ''',
)

preview_request = {
    "subject_version_id": 12,
    "unit_id": 4,
    "lesson_id": None,
    "activity_type": "guide_step",
    "activity_mode": "learn",
    "guide_step_id": 91,
    "source": "home_guide",
}
preview_success = {
    "success": True,
    "data": {
        "version": "contract-preview-v1",
        "generated_at": "2026-07-24T12:00:00+00:00",
        "activity": {
            "subject_version_id": 12,
            "subject_name": "الرياضيات",
            "unit_id": 4,
            "unit_title": "الوحدة الأولى",
            "lesson_id": None,
            "lesson_title": "",
            "activity_type": "guide_step",
            "activity_mode": "learn",
            "title": "خطوتك التعليمية التالية",
            "estimated_minutes": None,
            "question_count": None,
        },
        "eligibility": {"available": True, "status": "ready", "reason": "", "reason_code": ""},
        "subscription": {"available": True, "active": True, "status": "نشط", "ends_at": "2027-01-01", "blocking": False},
        "balances": {"hearts": 3, "gems": 10},
        "cost": {"required_hearts": 0, "heart_cost": 0, "gem_cost": 0},
        "attempts": {"available": False, "used": None, "remaining": None, "maximum": None, "reason": "لا ينطبق حد المحاولات على هذا النشاط."},
        "resume": {"available": False, "session_id": None, "status": "", "expires_at": "", "reason": ""},
    },
    "error": None,
    "request_id": "contract-activity-preview-001",
}
start_success = {
    "success": True,
    "data": {
        "session_id": "activity-session-001",
        "status": "created",
        "destination": "activity_session_pending_ui",
        "replayed": False,
        "debit": {"heart_debited": 0, "gems_debited": 0},
        "balances": {"hearts": 3, "gems": 10},
        "expires_at": "2026-07-24 14:00:00",
    },
    "error": None,
    "request_id": "contract-activity-start-001",
}
write("api-contract/fixtures/activity-preview-request.json", json.dumps(preview_request, ensure_ascii=False, indent=2) + "\n")
write("api-contract/fixtures/activity-preview-success.json", json.dumps(preview_success, ensure_ascii=False, indent=2) + "\n")
write("api-contract/fixtures/activity-start-success.json", json.dumps(start_success, ensure_ascii=False, indent=2) + "\n")

contract_path = ROOT / "api-contract/openapi.json"
contract = json.loads(contract_path.read_text(encoding="utf-8"))
error_response = {"description": "API envelope", "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ErrorResponse"}}}}

def responses(success_schema: str):
    return {
        "200": {"description": "API envelope", "content": {"application/json": {"schema": {"$ref": f"#/components/schemas/{success_schema}"}}}},
        "401": error_response,
        "409": error_response,
        "422": error_response,
        "429": error_response,
        "503": error_response,
    }

contract["paths"]["/api/v1/student/activity/preview"] = {
    "post": {
        "summary": "Read-only activity preparation preview",
        "security": [{"bearerAuth": []}],
        "requestBody": {"required": True, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ActivityPreparationRequest"}}}},
        "responses": responses("ActivityPreparationPreviewResponse"),
    }
}
contract["paths"]["/api/v1/student/activity/start"] = {
    "post": {
        "summary": "Create one idempotent server activity session",
        "security": [{"bearerAuth": []}],
        "parameters": [{"in": "header", "name": "Idempotency-Key", "required": True, "schema": {"type": "string", "minLength": 16, "maxLength": 128}}],
        "requestBody": {"required": True, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ActivityPreparationRequest"}}}},
        "responses": responses("ActivityStartResponse"),
    }
}
contract["paths"]["/api/v1/student/activity/start-status"] = {
    "get": {
        "summary": "Resolve an uncertain activity start using the original key",
        "security": [{"bearerAuth": []}],
        "parameters": [{"in": "query", "name": "idempotency_key", "required": True, "schema": {"type": "string", "minLength": 16, "maxLength": 128}}],
        "responses": {**responses("ActivityStartResponse"), "404": error_response},
    }
}

schemas = contract["components"]["schemas"]
nullable_integer = {"anyOf": [{"type": "integer"}, {"type": "null"}]}
nullable_string = {"anyOf": [{"type": "string"}, {"type": "null"}]}
schemas["ActivityPreparationRequest"] = {
    "type": "object",
    "additionalProperties": False,
    "required": ["subject_version_id","activity_type","activity_mode","source"],
    "properties": {
        "subject_version_id": {"type": "integer"},
        "unit_id": nullable_integer,
        "lesson_id": nullable_integer,
        "activity_type": {"type": "string"},
        "activity_mode": {"type": "string"},
        "guide_step_id": nullable_integer,
        "source": {"type": "string"},
    },
}
schemas["ActivityDescriptor"] = {
    "type": "object",
    "required": ["subject_version_id","subject_name","unit_id","unit_title","lesson_id","lesson_title","activity_type","activity_mode","title","estimated_minutes","question_count"],
    "properties": {
        "subject_version_id": {"type": "integer"}, "subject_name": {"type": "string"},
        "unit_id": nullable_integer, "unit_title": {"type": "string"},
        "lesson_id": nullable_integer, "lesson_title": {"type": "string"},
        "activity_type": {"type": "string"}, "activity_mode": {"type": "string"}, "title": {"type": "string"},
        "estimated_minutes": nullable_integer, "question_count": nullable_integer,
    },
}
schemas["ActivityEligibility"] = {"type":"object","required":["available","status","reason","reason_code"],"properties":{"available":{"type":"boolean"},"status":{"type":"string"},"reason":{"type":"string"},"reason_code":{"type":"string"}}}
schemas["ActivitySubscription"] = {"type":"object","required":["available","active","status","ends_at","blocking"],"properties":{"available":{"type":"boolean"},"active":{"type":"boolean"},"status":{"type":"string"},"ends_at":{"type":"string"},"blocking":{"type":"boolean"}}}
schemas["ActivityBalances"] = {"type":"object","required":["hearts","gems"],"properties":{"hearts":{"type":"integer"},"gems":{"type":"integer"}}}
schemas["ActivityCost"] = {"type":"object","required":["required_hearts","heart_cost","gem_cost"],"properties":{"required_hearts":{"type":"integer"},"heart_cost":{"type":"integer"},"gem_cost":{"type":"integer"}}}
schemas["ActivityAttempts"] = {"type":"object","required":["available","used","remaining","maximum","reason"],"properties":{"available":{"type":"boolean"},"used":nullable_integer,"remaining":nullable_integer,"maximum":nullable_integer,"reason":{"type":"string"}}}
schemas["ActivityResume"] = {"type":"object","required":["available","session_id","status","expires_at","reason"],"properties":{"available":{"type":"boolean"},"session_id":nullable_string,"status":{"type":"string"},"expires_at":{"type":"string"},"reason":{"type":"string"}}}
schemas["ActivityPreparationPreviewData"] = {
    "type":"object",
    "required":["version","generated_at","activity","eligibility","subscription","balances","cost","attempts","resume"],
    "properties":{
        "version":{"type":"string"},"generated_at":{"type":"string"},
        "activity":{"$ref":"#/components/schemas/ActivityDescriptor"},
        "eligibility":{"$ref":"#/components/schemas/ActivityEligibility"},
        "subscription":{"$ref":"#/components/schemas/ActivitySubscription"},
        "balances":{"$ref":"#/components/schemas/ActivityBalances"},
        "cost":{"$ref":"#/components/schemas/ActivityCost"},
        "attempts":{"$ref":"#/components/schemas/ActivityAttempts"},
        "resume":{"$ref":"#/components/schemas/ActivityResume"},
    },
}
schemas["ActivityPreparationPreviewResponse"] = {
    "type":"object","required":["success","data","error","request_id"],
    "properties":{"success":{"type":"boolean"},"data":{"anyOf":[{"$ref":"#/components/schemas/ActivityPreparationPreviewData"},{"type":"null"}]},"error":{"anyOf":[{"$ref":"#/components/schemas/Error"},{"type":"null"}]},"request_id":{"type":"string"}},
}
schemas["ActivityDebit"] = {"type":"object","required":["heart_debited","gems_debited"],"properties":{"heart_debited":{"type":"integer"},"gems_debited":{"type":"integer"}}}
schemas["ActivityStartData"] = {
    "type":"object","required":["session_id","status","destination","replayed","debit","balances","expires_at"],
    "properties":{"session_id":{"type":"string"},"status":{"type":"string"},"destination":{"type":"string"},"replayed":{"type":"boolean"},"debit":{"$ref":"#/components/schemas/ActivityDebit"},"balances":{"$ref":"#/components/schemas/ActivityBalances"},"expires_at":{"type":"string"}},
}
schemas["ActivityStartResponse"] = {
    "type":"object","required":["success","data","error","request_id"],
    "properties":{"success":{"type":"boolean"},"data":{"anyOf":[{"$ref":"#/components/schemas/ActivityStartData"},{"type":"null"}]},"error":{"anyOf":[{"$ref":"#/components/schemas/Error"},{"type":"null"}]},"request_id":{"type":"string"}},
}
contract_path.write_text(json.dumps(contract, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

validator = ROOT / "scripts/validate-api-contract.py"
text = validator.read_text(encoding="utf-8")
text = text.replace(
    '    "/api/v1/student/push-token": (("put", "delete"), "server-hostinger/public_html/api/v1/student/push-token.php"),\n',
    '    "/api/v1/student/push-token": (("put", "delete"), "server-hostinger/public_html/api/v1/student/push-token.php"),\n'
    '    "/api/v1/student/activity/preview": (("post",), "server-hostinger/public_html/api/v1/student/activity/preview.php"),\n'
    '    "/api/v1/student/activity/start": (("post",), "server-hostinger/public_html/api/v1/student/activity/start.php"),\n'
    '    "/api/v1/student/activity/start-status": (("get",), "server-hostinger/public_html/api/v1/student/activity/start-status.php"),\n',
    1,
)
text = text.replace(
    '    "push-token-success.json": "PushTokenResponse",\n',
    '    "push-token-success.json": "PushTokenResponse",\n'
    '    "activity-preview-success.json": "ActivityPreparationPreviewResponse",\n'
    '    "activity-start-success.json": "ActivityStartResponse",\n',
    1,
)
old = '''request_fixture = ROOT / "api-contract/fixtures/push-token-request.json"
validate_schema(json.loads(request_fixture.read_text()), {"$ref": "#/components/schemas/AndroidPushTokenRequest"}, request_fixture.name)
fixtures = sorted(item for item in (ROOT / "api-contract/fixtures").glob("*.json") if item != request_fixture)
'''
new = '''request_fixtures = {
    "push-token-request.json": "AndroidPushTokenRequest",
    "activity-preview-request.json": "ActivityPreparationRequest",
}
for name, schema_name in request_fixtures.items():
    request_fixture = ROOT / "api-contract/fixtures" / name
    validate_schema(json.loads(request_fixture.read_text()), {"$ref": f"#/components/schemas/{schema_name}"}, request_fixture.name)
fixtures = sorted(item for item in (ROOT / "api-contract/fixtures").glob("*.json") if item.name not in request_fixtures)
'''
if old not in text:
    raise SystemExit("validator request fixture block not found")
text = text.replace(old, new, 1)
validator.write_text(text, encoding="utf-8")

inventory = ROOT / "docs/PHASE_08_PRE_ACTIVITY_INVENTORY.md"
with inventory.open("a", encoding="utf-8") as handle:
    handle.write(dedent(r'''

    ## نتيجة الجرد والتنفيذ الفعلي

    - ثُبتت ثلاثة مسارات مستقلة تحت `/api/v1/student/activity`: المعاينة، البدء idempotent، والتحقق من نتيجة البدء غير المؤكدة.
    - المعاينة قراءة فقط ولا تنشئ جلسة ولا تخصم رصيدًا.
    - جلسات النشاط تستخدم migration مراجعة فقط في `server-hostinger/database/20260724_api_activity_sessions.sql`، ولم تُشغّل على الإنتاج.
    - القلب مطلوب كشرط أهلية للأنشطة المحددة، لكن لا يحدث خصم في هذه المرحلة لأن موضع الخصم النهائي يتبع محرك الاختبارات.
    - سجل المحاولات يعاد بحالة صريحة غير متاحة بدل اختراع أرقام، مع تثبيت الحد المعروف (6) دون ادعاء عدد مستخدم.
    - Android يحفظ مفتاح منع التكرار وبصمة الطلب، ويستعيد العملية أو يتحقق منها بالمفتاح نفسه عند انقطاع الشبكة.
    - خطوة الموجّه في الصفحة الرئيسية أصبحت تمر عبر شاشة التجهيز بدل الانتقال المباشر إلى واجهة المادة المؤقتة.
    - واجهة الجلسة التالية مؤقتة وآمنة، ولا تدّعي تنفيذ واجهات الاختبارات الخمسة قبل مرحلتها.
    '''))

print("Phase 08 server and API contract implementation applied.")
