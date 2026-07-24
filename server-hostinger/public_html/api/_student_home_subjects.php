<?php
declare(strict_types=1);

/**
 * Return the curriculum subjects available to the authenticated student.
 *
 * The production database has evolved over time, so this adapter detects the
 * optional ordering and activation columns before composing the read-only
 * query. It falls back to the legacy state-backed list when the curriculum
 * relationship is not available in an older installation.
 */
function api_student_home_table_columns(PDO $pdo, string $table): array
{
    static $cache = [];
    if (isset($cache[$table])) {
        return $cache[$table];
    }

    $allowed = ['app_users', 'cities', 'subject_versions', 'subjects'];
    if (!in_array($table, $allowed, true)) {
        return [];
    }

    try {
        $rows = $pdo->query("SHOW COLUMNS FROM `{$table}`")->fetchAll(PDO::FETCH_ASSOC) ?: [];
        $cache[$table] = array_fill_keys(array_map(static fn(array $row): string => (string)$row['Field'], $rows), true);
    } catch (Throwable) {
        $cache[$table] = [];
    }
    return $cache[$table];
}

function api_student_home_curriculum_subjects(PDO $pdo, int $studentId): array
{
    $userColumns = api_student_home_table_columns($pdo, 'app_users');
    $cityColumns = api_student_home_table_columns($pdo, 'cities');
    $versionColumns = api_student_home_table_columns($pdo, 'subject_versions');
    $subjectColumns = api_student_home_table_columns($pdo, 'subjects');

    if (!isset($userColumns['grade_id'], $userColumns['city_id'], $cityColumns['curriculum_id'])) {
        return api_student_home_subjects($pdo, $studentId);
    }
    if (!isset($versionColumns['subject_id'], $versionColumns['grade_id'], $versionColumns['curriculum_id'])) {
        return api_student_home_subjects($pdo, $studentId);
    }

    try {
        $student = $pdo->prepare(
            'SELECT u.grade_id, c.curriculum_id '
            . 'FROM app_users u JOIN cities c ON c.id=u.city_id '
            . 'WHERE u.id=? AND u.role=\'student\' LIMIT 1'
        );
        $student->execute([$studentId]);
        $scope = $student->fetch(PDO::FETCH_ASSOC) ?: null;
        $gradeId = (int)($scope['grade_id'] ?? 0);
        $curriculumId = (int)($scope['curriculum_id'] ?? 0);
        if ($gradeId <= 0 || $curriculumId <= 0) {
            return api_student_home_subjects($pdo, $studentId);
        }

        $filters = ['sv.grade_id=?', 'sv.curriculum_id=?'];
        if (isset($versionColumns['is_active'])) {
            $filters[] = 'sv.is_active=1';
        }
        if (isset($subjectColumns['is_active'])) {
            $filters[] = 's.is_active=1';
        }

        $order = match (true) {
            isset($versionColumns['sort_order']) => 'sv.sort_order ASC, s.name ASC, sv.id ASC',
            isset($subjectColumns['sort_order']) => 's.sort_order ASC, s.name ASC, sv.id ASC',
            isset($subjectColumns['display_order']) => 's.display_order ASC, s.name ASC, sv.id ASC',
            default => 's.name ASC, sv.id ASC',
        };

        $statement = $pdo->prepare(
            'SELECT sv.id AS subject_version_id, s.name, COALESCE(ss.hearts,3) AS hearts '
            . 'FROM subject_versions sv '
            . 'JOIN subjects s ON s.id=sv.subject_id '
            . 'LEFT JOIN student_subject_state ss ON ss.student_id=? AND ss.subject_version_id=sv.id '
            . 'WHERE ' . implode(' AND ', $filters) . ' '
            . "ORDER BY {$order} LIMIT 12"
        );
        $statement->execute([$studentId, $gradeId, $curriculumId]);

        return array_map(
            static fn(array $row): array => [
                'subject_version_id' => max(0, (int)($row['subject_version_id'] ?? 0)),
                'name' => trim((string)($row['name'] ?? '')),
                'hearts' => max(0, (int)($row['hearts'] ?? 3)),
                'progress_percent' => null,
            ],
            $statement->fetchAll(PDO::FETCH_ASSOC) ?: [],
        );
    } catch (Throwable) {
        return api_student_home_subjects($pdo, $studentId);
    }
}
