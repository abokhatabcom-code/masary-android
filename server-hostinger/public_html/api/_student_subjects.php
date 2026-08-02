<?php
declare(strict_types=1);

require_once __DIR__ . '/_student_home.php';
require_once __DIR__ . '/_student_home_subjects.php';

function api_student_subjects_profile_value(array $profile, array $keys): string
{
    foreach ($keys as $key) {
        $value = trim((string)($profile[$key] ?? ''));
        if ($value !== '') {
            return $value;
        }
    }
    return '';
}

function api_student_subjects_academic_context(PDO $pdo, int $studentId): array
{
    $profile = [];
    if (function_exists('ik_dash_profile')) {
        try {
            $profile = (array)ik_dash_profile($pdo, $studentId);
        } catch (Throwable) {
            $profile = [];
        }
    }

    $context = [
        'grade_name' => api_student_subjects_profile_value($profile, ['grade_name', 'grade', 'class_name']),
        'department_name' => api_student_subjects_profile_value($profile, ['department_name', 'department', 'section_name']),
        'city_name' => api_student_subjects_profile_value($profile, ['city_name', 'city']),
        'curriculum_name' => api_student_subjects_profile_value($profile, ['curriculum_name', 'curriculum']),
    ];
    $available = implode('', $context) !== '';

    return [
        'available' => $available,
        ...$context,
        'reason' => $available ? '' : 'لم يتوفر سياق أكاديمي نصي مؤكد لهذا الحساب في المصدر الحالي.',
    ];
}

function api_student_subjects_table_columns(PDO $pdo, string $table): array
{
    if ($table !== 'student_last_activity') {
        return [];
    }
    try {
        $rows = $pdo->query('SHOW COLUMNS FROM `student_last_activity`')->fetchAll(PDO::FETCH_ASSOC) ?: [];
        return array_fill_keys(
            array_map(static fn(array $row): string => (string)($row['Field'] ?? ''), $rows),
            true,
        );
    } catch (Throwable) {
        return [];
    }
}

function api_student_subjects_last_activity(PDO $pdo, int $studentId): array
{
    $columns = api_student_subjects_table_columns($pdo, 'student_last_activity');
    if (!isset($columns['student_id'], $columns['subject_version_id'])) {
        return [];
    }

    $select = ['subject_version_id'];
    foreach (['unit_id', 'mode', 'updated_at'] as $optional) {
        if (isset($columns[$optional])) {
            $select[] = $optional;
        }
    }
    $order = isset($columns['updated_at']) ? 'updated_at DESC' : 'subject_version_id ASC';

    try {
        $statement = $pdo->prepare(
            'SELECT ' . implode(',', $select)
            . ' FROM student_last_activity WHERE student_id=? ORDER BY ' . $order
        );
        $statement->execute([$studentId]);
        $result = [];
        foreach ($statement->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
            $subjectVersionId = (int)($row['subject_version_id'] ?? 0);
            if ($subjectVersionId <= 0 || isset($result[$subjectVersionId])) {
                continue;
            }
            $unitId = (int)($row['unit_id'] ?? 0);
            $result[$subjectVersionId] = [
                'available' => true,
                'unit_id' => $unitId > 0 ? $unitId : null,
                'mode' => trim((string)($row['mode'] ?? '')),
                'updated_at' => trim((string)($row['updated_at'] ?? '')),
                'reason' => '',
            ];
        }
        return $result;
    } catch (Throwable) {
        return [];
    }
}

function api_student_subjects_payload(PDO $pdo, array $session): array
{
    $studentId = (int)($session['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }

    $academic = api_student_subjects_academic_context($pdo, $studentId);
    $rawSubjects = api_student_home_curriculum_subjects($pdo, $studentId, 100);
    $lastActivity = api_student_subjects_last_activity($pdo, $studentId);
    $subjects = [];

    foreach ($rawSubjects as $row) {
        $subjectVersionId = max(0, (int)($row['subject_version_id'] ?? 0));
        $name = trim((string)($row['name'] ?? ''));
        if ($subjectVersionId <= 0 || $name === '') {
            continue;
        }
        $progress = $row['progress_percent'] ?? null;
        $progressAvailable = is_int($progress) || is_float($progress) || ctype_digit((string)$progress);
        $activity = $lastActivity[$subjectVersionId] ?? [
            'available' => false,
            'unit_id' => null,
            'mode' => '',
            'updated_at' => '',
            'reason' => 'لا يوجد نشاط سابق مؤكد لهذه المادة.',
        ];

        $subjects[] = [
            'subject_version_id' => $subjectVersionId,
            'name' => $name,
            'hearts' => max(0, (int)($row['hearts'] ?? 0)),
            'curriculum_label' => (string)($academic['curriculum_name'] ?? ''),
            'progress' => [
                'available' => $progressAvailable,
                'percent' => $progressAvailable ? max(0, min(100, (int)$progress)) : null,
                'reason' => $progressAvailable ? '' : 'لم يتوفر مصدر تقدم مؤكد لقائمة المواد.',
            ],
            'media' => [
                'available' => false,
                'key' => null,
                'reason' => 'لم يُربط مفتاح وسائط مؤكد بالمادة بعد.',
            ],
            'access' => [
                'available' => false,
                'status' => 'unknown',
                'reason' => 'تظهر المادة لأنها مرتبطة بالسياق الأكاديمي، لكن سياسة الوصول التفصيلية غير مربوطة بهذه القائمة بعد.',
            ],
            'last_activity' => $activity,
        ];
    }

    $generatedAt = gmdate(DATE_ATOM);
    $versionPayload = [
        'student_id' => $studentId,
        'academic' => $academic,
        'subjects' => $subjects,
    ];

    return [
        'student_id' => (string)$studentId,
        'version' => hash(
            'sha256',
            json_encode($versionPayload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
        ),
        'generated_at' => $generatedAt,
        'complete' => count($rawSubjects) < 100,
        'academic' => $academic,
        'subjects' => $subjects,
        'empty' => [
            'is_empty' => $subjects === [],
            'reason' => $subjects === [] ? 'لا توجد مواد مرتبطة بحساب الطالب حاليًا.' : '',
        ],
    ];
}
