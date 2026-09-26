<?php
declare(strict_types=1);

/**
 * Shared read-only subject selection used by Home preview and the full
 * subjects page. The limit is always normalized before being applied.
 */
function api_student_home_subject_effective_hearts(int $hearts, ?string $refillDate): int
{
    $hearts = max(0, min(3, $hearts));
    $refillDate = trim((string)$refillDate);
    $today = (new DateTimeImmutable('now', new DateTimeZone('Asia/Aden')))->format('Y-m-d');
    return $refillDate !== '' && $refillDate !== $today ? 3 : $hearts;
}

function api_student_home_subject_state_columns(PDO $pdo): array
{
    static $cache = [];
    $driver = strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME));
    if (isset($cache[$driver])) {
        return $cache[$driver];
    }
    try {
        if ($driver === 'sqlite') {
            $rows = $pdo->query('PRAGMA table_info(student_subject_state)')->fetchAll(PDO::FETCH_ASSOC) ?: [];
            return $cache[$driver] = array_fill_keys(
                array_filter(array_map(static fn(array $row): string => (string)($row['name'] ?? ''), $rows)),
                true,
            );
        }
        $rows = $pdo->query('SHOW COLUMNS FROM student_subject_state')->fetchAll(PDO::FETCH_ASSOC) ?: [];
        return $cache[$driver] = array_fill_keys(
            array_filter(array_map(static fn(array $row): string => (string)($row['Field'] ?? ''), $rows)),
            true,
        );
    } catch (Throwable) {
        return $cache[$driver] = [];
    }
}

function api_student_home_subject_limit(int $limit): int
{
    return max(1, min(100, $limit));
}

/** @return array{school_id:int,grade_id:int,city_id:int}|null */
function api_student_home_student_scope(PDO $pdo, int $studentId): ?array
{
    if ($studentId <= 0) {
        return null;
    }
    try {
        $statement = $pdo->prepare(
            "SELECT COALESCE(school_id,0) AS school_id, COALESCE(grade_id,0) AS grade_id, "
            . "COALESCE(city_id,0) AS city_id FROM app_users "
            . "WHERE id=? AND role='student' LIMIT 1"
        );
        $statement->execute([$studentId]);
        $row = $statement->fetch(PDO::FETCH_ASSOC) ?: null;
        if (!$row) {
            return null;
        }
        return [
            'school_id' => max(0, (int)($row['school_id'] ?? 0)),
            'grade_id' => max(0, (int)($row['grade_id'] ?? 0)),
            'city_id' => max(0, (int)($row['city_id'] ?? 0)),
        ];
    } catch (Throwable) {
        return null;
    }
}

/** @return array<int,int> */
function api_student_home_subject_hearts(PDO $pdo, int $studentId, array $subjectVersionIds): array
{
    $ids = array_values(array_unique(array_filter(
        array_map('intval', $subjectVersionIds),
        static fn(int $id): bool => $id > 0,
    )));
    if ($studentId <= 0 || $ids === []) {
        return [];
    }

    try {
        $placeholders = implode(',', array_fill(0, count($ids), '?'));
        $columns = api_student_home_subject_state_columns($pdo);
        $hasRefillDate = isset($columns['hearts_refill_date']);
        $select = 'subject_version_id, hearts' . ($hasRefillDate ? ', hearts_refill_date' : '');
        $statement = $pdo->prepare(
            "SELECT {$select} FROM student_subject_state "
            . "WHERE student_id=? AND subject_version_id IN ({$placeholders})"
        );
        $statement->execute(array_merge([$studentId], $ids));
        $out = [];
        foreach ($statement->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
            $subjectVersionId = (int)($row['subject_version_id'] ?? 0);
            if ($subjectVersionId > 0) {
                $out[$subjectVersionId] = api_student_home_subject_effective_hearts(
                    (int)($row['hearts'] ?? 3),
                    $hasRefillDate ? (string)($row['hearts_refill_date'] ?? '') : '',
                );
            }
        }
        return $out;
    } catch (Throwable) {
        return [];
    }
}

/**
 * Resolve the exact material versions through the same business resolver used
 * by the smart study guide and the legacy student experience. A null result
 * means that the resolver is unavailable and allows the compatibility fallback;
 * an empty array is an authoritative empty curriculum and must stay empty.
 *
 * @return list<array{subject_version_id:int,name:string,hearts:int,progress_percent:null}>|null
 */
function api_student_home_effective_subjects(PDO $pdo, int $studentId, int $limit): ?array
{
    $limit = api_student_home_subject_limit($limit);

    if (!function_exists('curriculum_effective_subjects') && defined('IKHTABIRNI_PUBLIC_ROOT')) {
        $helper = IKHTABIRNI_PUBLIC_ROOT . '/includes/curriculum_effective.php';
        if (is_file($helper)) {
            require_once $helper;
        }
    }
    if (!function_exists('curriculum_effective_subjects')) {
        return null;
    }

    $scope = api_student_home_student_scope($pdo, $studentId);
    if (!$scope || $scope['grade_id'] <= 0) {
        return null;
    }

    try {
        $resolved = curriculum_effective_subjects(
            $pdo,
            $scope['school_id'],
            $scope['grade_id'],
            $scope['city_id'] > 0 ? $scope['city_id'] : null,
        );
    } catch (Throwable) {
        return null;
    }

    $subjectVersionIds = [];
    foreach ($resolved as $row) {
        $subjectVersionId = (int)($row['sv_id'] ?? 0);
        if ($subjectVersionId > 0) {
            $subjectVersionIds[] = $subjectVersionId;
        }
    }
    $hearts = api_student_home_subject_hearts($pdo, $studentId, $subjectVersionIds);

    $subjects = [];
    $seen = [];
    foreach ($resolved as $row) {
        $subjectVersionId = (int)($row['sv_id'] ?? 0);
        $name = trim((string)($row['subject_name'] ?? ''));
        if ($subjectVersionId <= 0 || $name === '' || isset($seen[$subjectVersionId])) {
            continue;
        }
        $seen[$subjectVersionId] = true;
        $subjects[] = [
            'subject_version_id' => $subjectVersionId,
            'name' => $name,
            'hearts' => $hearts[$subjectVersionId] ?? 3,
            'progress_percent' => null,
        ];
        if (count($subjects) >= $limit) {
            break;
        }
    }

    return $subjects;
}

function api_student_home_legacy_subjects(PDO $pdo, int $studentId, int $limit): array
{
    $limit = api_student_home_subject_limit($limit);
    try {
        $columns = api_student_home_subject_state_columns($pdo);
        $hasRefillDate = isset($columns['hearts_refill_date']);
        $refillSelect = $hasRefillDate ? ', ss.hearts_refill_date' : '';
        $stmt = $pdo->prepare(
            "SELECT ss.subject_version_id, s.name, ss.hearts{$refillSelect} "
            . "FROM student_subject_state ss "
            . "JOIN subject_versions sv ON sv.id=ss.subject_version_id "
            . "JOIN subjects s ON s.id=sv.subject_id "
            . "WHERE ss.student_id=? ORDER BY s.name, ss.subject_version_id LIMIT {$limit}"
        );
        $stmt->execute([$studentId]);
        return array_map(
            static fn(array $row): array => [
                'subject_version_id' => max(0, (int)($row['subject_version_id'] ?? 0)),
                'name' => trim((string)($row['name'] ?? '')),
                'hearts' => api_student_home_subject_effective_hearts(
                    (int)($row['hearts'] ?? 3),
                    $hasRefillDate ? (string)($row['hearts_refill_date'] ?? '') : '',
                ),
                'progress_percent' => null,
            ],
            $stmt->fetchAll(PDO::FETCH_ASSOC) ?: [],
        );
    } catch (Throwable) {
        return [];
    }
}

/**
 * Return curriculum subjects available to the authenticated student.
 *
 * The official resolver applies city curriculum, school curriculum overrides,
 * active versions and student visibility exactly as the existing guide does.
 * The legacy state-backed list is used only when that resolver cannot be loaded
 * or the old account has no academic scope at all.
 */
function api_student_home_curriculum_subjects(PDO $pdo, int $studentId, int $limit = 12): array
{
    $resolved = api_student_home_effective_subjects($pdo, $studentId, $limit);
    if ($resolved !== null) {
        return $resolved;
    }
    return api_student_home_legacy_subjects($pdo, $studentId, $limit);
}
