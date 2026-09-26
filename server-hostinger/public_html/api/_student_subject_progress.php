<?php
declare(strict_types=1);

/**
 * Read-only learning-path progress projection for the native student app.
 *
 * This mirrors the established web-platform rules without creating or mutating
 * progress rows:
 * - settings live on subject_versions;
 * - unit progress lives in student_unit_state;
 * - lesson-mode progress lives in student_content_node_state;
 * - the first item is open, then the next item follows the configured
 *   threshold unless unlock_mode=free;
 * - within_unit resets the lesson sequence for every unit.
 */

function api_student_subject_progress_columns(PDO $pdo, string $table): array
{
    static $cache = [];
    $allowed = [
        'subject_versions',
        'student_unit_state',
        'student_content_node_state',
        'content_nodes',
        'units',
        'lessons',
    ];
    if (!in_array($table, $allowed, true)) {
        return [];
    }
    if (isset($cache[$table])) {
        return $cache[$table];
    }
    try {
        $driver = strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME));
        if ($driver === 'sqlite') {
            $rows = $pdo->query("PRAGMA table_info(`{$table}`)")->fetchAll(PDO::FETCH_ASSOC) ?: [];
            return $cache[$table] = array_fill_keys(
                array_filter(array_map(static fn(array $row): string => (string)($row['name'] ?? ''), $rows)),
                true,
            );
        }
        $rows = $pdo->query("SHOW COLUMNS FROM `{$table}`")->fetchAll(PDO::FETCH_ASSOC) ?: [];
        return $cache[$table] = array_fill_keys(
            array_filter(array_map(static fn(array $row): string => (string)($row['Field'] ?? ''), $rows)),
            true,
        );
    } catch (Throwable) {
        return $cache[$table] = [];
    }
}

function api_student_subject_progress_settings(PDO $pdo, int $subjectVersionId): array
{
    $defaults = [
        'progress_mode' => 'unit',
        'unlock_mode' => 'sequential',
        'unlock_threshold_percent' => 30.0,
        'review_progress_cap_points' => 150.0,
    ];
    $columns = api_student_subject_progress_columns($pdo, 'subject_versions');
    if (!isset($columns['id'])) {
        return $defaults;
    }
    $select = [];
    foreach (array_keys($defaults) as $column) {
        if (isset($columns[$column])) {
            $select[] = $column;
        }
    }
    if ($select === []) {
        return $defaults;
    }
    try {
        $statement = $pdo->prepare(
            'SELECT ' . implode(',', $select) . ' FROM subject_versions WHERE id=? LIMIT 1'
        );
        $statement->execute([$subjectVersionId]);
        $row = $statement->fetch(PDO::FETCH_ASSOC) ?: [];
    } catch (Throwable) {
        $row = [];
    }

    $progressMode = strtolower(trim((string)($row['progress_mode'] ?? $defaults['progress_mode'])));
    if (!in_array($progressMode, ['unit', 'lesson'], true)) {
        $progressMode = 'unit';
    }
    $unlockMode = strtolower(trim((string)($row['unlock_mode'] ?? $defaults['unlock_mode'])));
    if (!in_array($unlockMode, ['sequential', 'within_unit', 'free'], true)) {
        $unlockMode = 'sequential';
    }
    $threshold = (float)($row['unlock_threshold_percent'] ?? $defaults['unlock_threshold_percent']);
    if ($threshold <= 0.0 || $threshold > 100.0) {
        $threshold = $defaults['unlock_threshold_percent'];
    }
    $reviewCap = (float)($row['review_progress_cap_points'] ?? $defaults['review_progress_cap_points']);
    if ($reviewCap <= 0.0) {
        $reviewCap = $defaults['review_progress_cap_points'];
    }

    return [
        'progress_mode' => $progressMode,
        'unlock_mode' => $unlockMode,
        'unlock_threshold_percent' => $threshold,
        'review_progress_cap_points' => $reviewCap,
    ];
}

function api_student_subject_progress_state_map(
    PDO $pdo,
    string $table,
    string $idColumn,
    int $studentId,
    array $ids,
): array {
    $columns = api_student_subject_progress_columns($pdo, $table);
    if (!isset($columns['student_id'], $columns[$idColumn])) {
        return [];
    }
    $ids = array_values(array_unique(array_filter(array_map('intval', $ids), static fn(int $id): bool => $id > 0)));
    if ($ids === []) {
        return [];
    }

    $select = [$idColumn];
    foreach (['learn_attempt_done', 'learn_xp_earned', 'review_xp_total', 'review_attempt_count', 'last_score_correct', 'last_score_total', 'updated_at'] as $column) {
        if (isset($columns[$column])) {
            $select[] = $column;
        }
    }
    $placeholders = implode(',', array_fill(0, count($ids), '?'));
    try {
        $statement = $pdo->prepare(
            'SELECT ' . implode(',', $select)
            . " FROM {$table} WHERE student_id=? AND {$idColumn} IN ({$placeholders})"
        );
        $statement->execute(array_merge([$studentId], $ids));
        $rows = $statement->fetchAll(PDO::FETCH_ASSOC) ?: [];
    } catch (Throwable) {
        return [];
    }

    $out = [];
    foreach ($rows as $row) {
        $id = (int)($row[$idColumn] ?? 0);
        if ($id > 0) {
            $out[$id] = $row;
        }
    }
    return $out;
}

function api_student_subject_node_threshold_map(
    PDO $pdo,
    array $nodeIds,
    float $defaultThreshold,
): array {
    $columns = api_student_subject_progress_columns($pdo, 'content_nodes');
    if (!isset($columns['id'], $columns['unlock_threshold_percent'])) {
        return [];
    }
    $nodeIds = array_values(array_unique(array_filter(array_map('intval', $nodeIds), static fn(int $id): bool => $id > 0)));
    if ($nodeIds === []) {
        return [];
    }
    $placeholders = implode(',', array_fill(0, count($nodeIds), '?'));
    try {
        $statement = $pdo->prepare(
            "SELECT id, unlock_threshold_percent FROM content_nodes WHERE id IN ({$placeholders})"
        );
        $statement->execute($nodeIds);
        $rows = $statement->fetchAll(PDO::FETCH_ASSOC) ?: [];
    } catch (Throwable) {
        return [];
    }
    $out = [];
    foreach ($rows as $row) {
        $id = (int)($row['id'] ?? 0);
        $threshold = (float)($row['unlock_threshold_percent'] ?? 0);
        if ($id > 0 && $threshold > 0.0 && $threshold <= 100.0) {
            $out[$id] = $threshold;
        }
    }
    return $out;
}

function api_student_subject_progress_percent(array $state, float $reviewCap): float
{
    $reviewTotal = max(0.0, (float)($state['review_xp_total'] ?? 0));
    return round(min(100.0, ($reviewTotal / max(1.0, $reviewCap)) * 100.0), 1);
}

function api_student_subject_progress_status(
    bool $open,
    array $state,
    bool $isCurrent,
    string $lockedReason,
): array {
    if (!$open) {
        return ['status' => 'locked', 'reason' => $lockedReason];
    }
    if ((int)($state['learn_attempt_done'] ?? 0) === 1) {
        return ['status' => 'completed', 'reason' => ''];
    }
    $learnXp = max(0.0, (float)($state['learn_xp_earned'] ?? 0));
    $reviewXp = max(0.0, (float)($state['review_xp_total'] ?? 0));
    if ($isCurrent || $learnXp > 0.0 || $reviewXp > 0.0) {
        return ['status' => 'in_progress', 'reason' => ''];
    }
    return ['status' => 'ready', 'reason' => ''];
}

function api_student_subject_progress_locked_reason(float $threshold, string $mode): string
{
    $label = abs($threshold - round($threshold)) < 0.00001
        ? (string)((int)round($threshold))
        : rtrim(rtrim(number_format($threshold, 1, '.', ''), '0'), '.');
    $previous = $mode === 'lesson' ? 'الدرس السابق' : 'الوحدة السابقة';
    return "يفتح بإكمال {$label}% من {$previous}.";
}

function api_student_subject_order_column(array $columns): string
{
    foreach (['unit_order', 'lesson_order', 'sort_order', 'position', 'order_index', 'display_order', 'sequence', 'id'] as $column) {
        if (isset($columns[$column])) {
            return $column;
        }
    }
    return 'id';
}

function api_student_subject_lesson_access_state(
    PDO $pdo,
    int $studentId,
    int $subjectVersionId,
    int $unitId,
    int $lessonId,
): array {
    if ($studentId <= 0 || $subjectVersionId <= 0 || $unitId <= 0 || $lessonId <= 0) {
        return [
            'available' => false,
            'open' => false,
            'reason' => 'بيانات الدرس غير مكتملة.',
        ];
    }

    $unitColumns = api_student_subject_progress_columns($pdo, 'units');
    $lessonColumns = api_student_subject_progress_columns($pdo, 'lessons');
    if (!isset($unitColumns['id'], $unitColumns['subject_version_id'])
        || !isset($lessonColumns['id'], $lessonColumns['unit_id'])) {
        return [
            'available' => false,
            'open' => false,
            'reason' => 'تعذر التحقق من بنية الوحدات والدروس.',
        ];
    }

    $unitPartColumn = isset($unitColumns['part']) ? 'part' : null;
    $unitOrder = api_student_subject_order_column($unitColumns);
    $lessonOrder = api_student_subject_order_column($lessonColumns);

    $unitSelect = ['id'];
    if ($unitPartColumn !== null) {
        $unitSelect[] = $unitPartColumn;
    }
    $unitSelect[] = $unitOrder;
    $where = ['subject_version_id=?'];
    if (isset($unitColumns['is_active'])) {
        $where[] = 'is_active=1';
    }
    try {
        $statement = $pdo->prepare(
            'SELECT ' . implode(',', array_unique($unitSelect))
            . ' FROM units WHERE ' . implode(' AND ', $where)
            . " ORDER BY " . ($unitPartColumn !== null ? "`{$unitPartColumn}` ASC, " : '')
            . "`{$unitOrder}` ASC, id ASC"
        );
        $statement->execute([$subjectVersionId]);
        $units = $statement->fetchAll(PDO::FETCH_ASSOC) ?: [];
    } catch (Throwable) {
        return [
            'available' => false,
            'open' => false,
            'reason' => 'تعذر قراءة ترتيب الوحدات.',
        ];
    }

    $targetUnit = null;
    foreach ($units as $unit) {
        if ((int)($unit['id'] ?? 0) === $unitId) {
            $targetUnit = $unit;
            break;
        }
    }
    if ($targetUnit === null) {
        return [
            'available' => true,
            'open' => false,
            'reason' => 'الوحدة المطلوبة غير متاحة ضمن هذه المادة.',
        ];
    }
    $targetPart = $unitPartColumn !== null ? (int)($targetUnit[$unitPartColumn] ?? 0) : 0;

    $lessonSelect = ['id', 'unit_id', $lessonOrder];
    if (isset($lessonColumns['part'])) {
        $lessonSelect[] = 'part';
    }
    if (isset($lessonColumns['content_node_id'])) {
        $lessonSelect[] = 'content_node_id';
    }
    $lessonWhere = [];
    $params = [];
    if (isset($lessonColumns['subject_version_id'])) {
        $lessonWhere[] = 'l.subject_version_id=?';
        $params[] = $subjectVersionId;
        $join = '';
    } else {
        $join = ' JOIN units u ON u.id=l.unit_id';
        $lessonWhere[] = 'u.subject_version_id=?';
        $params[] = $subjectVersionId;
        if (isset($unitColumns['is_active'])) {
            $lessonWhere[] = 'u.is_active=1';
        }
    }
    if (isset($lessonColumns['is_active'])) {
        $lessonWhere[] = 'l.is_active=1';
    }

    try {
        $qualified = array_map(static fn(string $column): string => "l.`{$column}`", array_unique($lessonSelect));
        $statement = $pdo->prepare(
            'SELECT ' . implode(',', $qualified)
            . ' FROM lessons l' . $join
            . ' WHERE ' . implode(' AND ', $lessonWhere)
            . ' ORDER BY l.unit_id ASC, ' . "l.`{$lessonOrder}` ASC, l.id ASC"
        );
        $statement->execute($params);
        $lessons = $statement->fetchAll(PDO::FETCH_ASSOC) ?: [];
    } catch (Throwable) {
        return [
            'available' => false,
            'open' => false,
            'reason' => 'تعذر قراءة ترتيب الدروس.',
        ];
    }

    $targetFound = false;
    foreach ($lessons as $lesson) {
        if ((int)($lesson['id'] ?? 0) === $lessonId && (int)($lesson['unit_id'] ?? 0) === $unitId) {
            $targetFound = true;
            break;
        }
    }
    if (!$targetFound) {
        return [
            'available' => true,
            'open' => false,
            'reason' => 'الدرس المطلوب غير متاح ضمن هذه الوحدة.',
        ];
    }

    $settings = api_student_subject_progress_settings($pdo, $subjectVersionId);
    $progressMode = (string)$settings['progress_mode'];
    $unlockMode = (string)$settings['unlock_mode'];
    $threshold = (float)$settings['unlock_threshold_percent'];
    $reviewCap = (float)$settings['review_progress_cap_points'];

    if ($unlockMode === 'free') {
        return ['available' => true, 'open' => true, 'reason' => ''];
    }

    if ($progressMode === 'unit') {
        $partUnits = array_values(array_filter(
            $units,
            static fn(array $unit): bool => ($unitPartColumn === null ? 0 : (int)($unit[$unitPartColumn] ?? 0)) === $targetPart,
        ));
        $index = null;
        foreach ($partUnits as $position => $unit) {
            if ((int)($unit['id'] ?? 0) === $unitId) {
                $index = $position;
                break;
            }
        }
        if ($index === null || $index === 0) {
            return ['available' => true, 'open' => true, 'reason' => ''];
        }
        $previousUnitId = (int)($partUnits[$index - 1]['id'] ?? 0);
        $states = api_student_subject_progress_state_map(
            $pdo,
            'student_unit_state',
            'unit_id',
            $studentId,
            [$previousUnitId],
        );
        $reviewPct = api_student_subject_progress_percent($states[$previousUnitId] ?? [], $reviewCap);
        $open = $reviewPct >= $threshold;
        return [
            'available' => true,
            'open' => $open,
            'reason' => $open ? '' : api_student_subject_progress_locked_reason($threshold, 'unit'),
        ];
    }

    $unitPartById = [];
    foreach ($units as $unit) {
        $id = (int)($unit['id'] ?? 0);
        if ($id > 0) {
            $unitPartById[$id] = $unitPartColumn === null ? 0 : (int)($unit[$unitPartColumn] ?? 0);
        }
    }

    $path = array_values(array_filter(
        $lessons,
        static function (array $lesson) use ($unlockMode, $unitId, $targetPart, $unitPartById): bool {
            $lessonUnitId = (int)($lesson['unit_id'] ?? 0);
            if ($unlockMode === 'within_unit') {
                return $lessonUnitId === $unitId;
            }
            return (int)($unitPartById[$lessonUnitId] ?? 0) === $targetPart;
        },
    ));

    $nodeIds = [];
    foreach ($path as $lesson) {
        $nodeId = (int)($lesson['content_node_id'] ?? 0);
        if ($nodeId > 0) {
            $nodeIds[] = $nodeId;
        }
    }
    $nodeStates = api_student_subject_progress_state_map(
        $pdo,
        'student_content_node_state',
        'content_node_id',
        $studentId,
        $nodeIds,
    );
    $nodeThresholds = api_student_subject_node_threshold_map($pdo, $nodeIds, $threshold);

    $previousPassed = true;
    foreach ($path as $position => $lesson) {
        $currentLessonId = (int)($lesson['id'] ?? 0);
        $open = $position === 0 || $previousPassed;
        if ($currentLessonId === $lessonId) {
            $reasonThreshold = $position > 0
                ? (float)($nodeThresholds[(int)($path[$position - 1]['content_node_id'] ?? 0)] ?? $threshold)
                : $threshold;
            return [
                'available' => true,
                'open' => $open,
                'reason' => $open ? '' : api_student_subject_progress_locked_reason($reasonThreshold, 'lesson'),
            ];
        }
        $nodeId = (int)($lesson['content_node_id'] ?? 0);
        $itemThreshold = (float)($nodeThresholds[$nodeId] ?? $threshold);
        $reviewPct = api_student_subject_progress_percent($nodeStates[$nodeId] ?? [], $reviewCap);
        $previousPassed = $reviewPct >= $itemThreshold;
    }

    return [
        'available' => true,
        'open' => false,
        'reason' => 'تعذر تحديد موقع الدرس في مسار التعلّم.',
    ];
}

function api_student_subject_apply_progress_states(
    PDO $pdo,
    int $studentId,
    int $subjectVersionId,
    array $units,
    array $standaloneLessons,
    array $lastActivity,
): array {
    $settings = api_student_subject_progress_settings($pdo, $subjectVersionId);
    $progressMode = $settings['progress_mode'];
    $unlockMode = $settings['unlock_mode'];
    $threshold = (float)$settings['unlock_threshold_percent'];
    $reviewCap = (float)$settings['review_progress_cap_points'];

    $unitIds = [];
    $nodeIds = [];
    foreach ($units as $unit) {
        $unitId = (int)($unit['id'] ?? 0);
        if ($unitId > 0) {
            $unitIds[] = $unitId;
        }
        foreach ((array)($unit['lessons'] ?? []) as $lesson) {
            $nodeId = (int)($lesson['_content_node_id'] ?? 0);
            if ($nodeId > 0) {
                $nodeIds[] = $nodeId;
            }
        }
    }
    foreach ($standaloneLessons as $lesson) {
        $nodeId = (int)($lesson['_content_node_id'] ?? 0);
        if ($nodeId > 0) {
            $nodeIds[] = $nodeId;
        }
    }

    $unitStates = api_student_subject_progress_state_map(
        $pdo,
        'student_unit_state',
        'unit_id',
        $studentId,
        $unitIds,
    );
    $nodeStates = api_student_subject_progress_state_map(
        $pdo,
        'student_content_node_state',
        'content_node_id',
        $studentId,
        $nodeIds,
    );
    $nodeThresholds = api_student_subject_node_threshold_map($pdo, $nodeIds, $threshold);

    if ($progressMode === 'unit') {
        $seenByPart = [];
        $previousPassedByPart = [];
        foreach ($units as $index => $unit) {
            $unitId = (int)($unit['id'] ?? 0);
            $part = (int)($unit['part_number'] ?? 0);
            $state = $unitStates[$unitId] ?? [];
            $isFirst = !isset($seenByPart[$part]);
            $open = $unlockMode === 'free'
                || $isFirst
                || !empty($previousPassedByPart[$part]);
            $reviewPct = api_student_subject_progress_percent($state, $reviewCap);
            $passed = $reviewPct >= $threshold;
            $seenByPart[$part] = true;
            $previousPassedByPart[$part] = $passed;

            $unit['state'] = api_student_subject_progress_status(
                $open,
                $state,
                $unitId === (int)($lastActivity['unit_id'] ?? 0),
                api_student_subject_progress_locked_reason($threshold, 'unit'),
            );
            $unit['progress'] = [
                'review_percent' => $reviewPct,
                'unlock_threshold_percent' => $threshold,
                'learn_completed' => (int)($state['learn_attempt_done'] ?? 0) === 1,
            ];

            foreach ((array)($unit['lessons'] ?? []) as $lessonIndex => $lesson) {
                unset($lesson['_content_node_id']);
                $lesson['state'] = $open
                    ? (($lesson['id'] ?? 0) === (int)($lastActivity['lesson_id'] ?? 0)
                        ? ['status' => 'in_progress', 'reason' => '']
                        : ['status' => 'ready', 'reason' => ''])
                    : ['status' => 'locked', 'reason' => api_student_subject_progress_locked_reason($threshold, 'unit')];
                $lesson['preparation'] = [
                    'available' => $open,
                    'reason' => $open ? '' : api_student_subject_progress_locked_reason($threshold, 'unit'),
                ];
                $unit['lessons'][$lessonIndex] = $lesson;
            }
            $units[$index] = $unit;
        }

        foreach ($standaloneLessons as $index => $lesson) {
            unset($lesson['_content_node_id']);
            $hasUnit = (int)($lesson['unit_id'] ?? 0) > 0;
            $lesson['state'] = $hasUnit
                ? ['status' => 'ready', 'reason' => '']
                : ['status' => 'unavailable', 'reason' => 'هذا الدرس غير مرتبط بوحدة قابلة للبدء.'];
            $lesson['preparation'] = [
                'available' => $hasUnit,
                'reason' => $hasUnit ? '' : 'هذا الدرس غير مرتبط بوحدة قابلة للبدء.',
            ];
            $standaloneLessons[$index] = $lesson;
        }
    } else {
        $flat = [];
        foreach ($units as $unitIndex => $unit) {
            foreach ((array)($unit['lessons'] ?? []) as $lessonIndex => $lesson) {
                $flat[] = [
                    'unit_index' => $unitIndex,
                    'lesson_index' => $lessonIndex,
                    'standalone_index' => null,
                    'unit_id' => (int)($unit['id'] ?? 0),
                    'part' => (int)($lesson['part_number'] ?? $unit['part_number'] ?? 0),
                    'lesson' => $lesson,
                ];
            }
        }
        foreach ($standaloneLessons as $lessonIndex => $lesson) {
            $flat[] = [
                'unit_index' => null,
                'lesson_index' => null,
                'standalone_index' => $lessonIndex,
                'unit_id' => (int)($lesson['unit_id'] ?? 0),
                'part' => (int)($lesson['part_number'] ?? 0),
                'lesson' => $lesson,
            ];
        }

        $seenByPart = [];
        $prevPassedByPart = [];
        $seenByUnit = [];
        $prevPassedByUnit = [];

        foreach ($flat as $entry) {
            $lesson = $entry['lesson'];
            $nodeId = (int)($lesson['_content_node_id'] ?? 0);
            $state = $nodeId > 0 ? ($nodeStates[$nodeId] ?? []) : [];
            $part = (int)$entry['part'];
            $unitId = (int)$entry['unit_id'];
            $itemThreshold = (float)($nodeThresholds[$nodeId] ?? $threshold);
            $reviewPct = api_student_subject_progress_percent($state, $reviewCap);

            if ($unlockMode === 'free') {
                $open = true;
            } elseif ($unlockMode === 'within_unit' && $unitId > 0) {
                $open = !isset($seenByUnit[$unitId]) || !empty($prevPassedByUnit[$unitId]);
            } else {
                $open = !isset($seenByPart[$part]) || !empty($prevPassedByPart[$part]);
            }

            $passed = $reviewPct >= $itemThreshold;
            $seenByPart[$part] = true;
            $prevPassedByPart[$part] = $passed;
            if ($unitId > 0) {
                $seenByUnit[$unitId] = true;
                $prevPassedByUnit[$unitId] = $passed;
            }

            $lesson['state'] = api_student_subject_progress_status(
                $open,
                $state,
                (int)($lesson['id'] ?? 0) === (int)($lastActivity['lesson_id'] ?? 0),
                api_student_subject_progress_locked_reason($itemThreshold, 'lesson'),
            );
            $lesson['progress'] = [
                'review_percent' => $reviewPct,
                'unlock_threshold_percent' => $itemThreshold,
                'learn_completed' => (int)($state['learn_attempt_done'] ?? 0) === 1,
            ];
            $canStart = $open && $unitId > 0;
            $lesson['preparation'] = [
                'available' => $canStart,
                'reason' => $canStart
                    ? ''
                    : ($open
                        ? 'هذا الدرس غير مرتبط بوحدة قابلة للبدء.'
                        : api_student_subject_progress_locked_reason($itemThreshold, 'lesson')),
            ];
            unset($lesson['_content_node_id']);

            if ($entry['unit_index'] !== null && $entry['lesson_index'] !== null) {
                $units[$entry['unit_index']]['lessons'][$entry['lesson_index']] = $lesson;
            } elseif ($entry['standalone_index'] !== null) {
                $standaloneLessons[$entry['standalone_index']] = $lesson;
            }
        }

        foreach ($units as $index => $unit) {
            $statuses = array_values(array_filter(array_map(
                static fn(array $lesson): string => (string)($lesson['state']['status'] ?? ''),
                (array)($unit['lessons'] ?? []),
            )));
            if ($statuses === []) {
                $unitId = (int)($unit['id'] ?? 0);
                $state = $unitStates[$unitId] ?? [];
                $unit['state'] = api_student_subject_progress_status(
                    true,
                    $state,
                    $unitId === (int)($lastActivity['unit_id'] ?? 0),
                    '',
                );
            } elseif (count(array_filter($statuses, static fn(string $status): bool => $status === 'completed')) === count($statuses)) {
                $unit['state'] = ['status' => 'completed', 'reason' => ''];
            } elseif (in_array('in_progress', $statuses, true)) {
                $unit['state'] = ['status' => 'in_progress', 'reason' => ''];
            } elseif (count(array_filter($statuses, static fn(string $status): bool => $status === 'locked')) === count($statuses)) {
                $unit['state'] = [
                    'status' => 'locked',
                    'reason' => api_student_subject_progress_locked_reason($threshold, 'lesson'),
                ];
            } else {
                $unit['state'] = ['status' => 'ready', 'reason' => ''];
            }
            $units[$index] = $unit;
        }
    }

    return [
        'units' => $units,
        'lessons' => $standaloneLessons,
        'settings' => [
            'progress_mode' => $progressMode,
            'unlock_mode' => $unlockMode,
            'unlock_threshold_percent' => $threshold,
            'review_progress_cap_points' => $reviewCap,
        ],
    ];
}
