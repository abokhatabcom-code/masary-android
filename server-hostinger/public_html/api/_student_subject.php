<?php
declare(strict_types=1);

require_once __DIR__ . '/_student_subjects.php';
require_once __DIR__ . '/_student_subject_progress.php';

function api_student_subject_columns(PDO $pdo, string $table): array
{
    static $cache = [];
    $allowed = [
        'subject_versions',
        'subjects',
        'student_subject_state',
        'units',
        'lessons',
        'student_last_activity',
    ];
    if (!in_array($table, $allowed, true)) {
        return [];
    }
    if (isset($cache[$table])) {
        return $cache[$table];
    }
    try {
        $rows = $pdo->query("SHOW COLUMNS FROM `{$table}`")->fetchAll(PDO::FETCH_ASSOC) ?: [];
        return $cache[$table] = array_fill_keys(
            array_map(static fn(array $row): string => (string)($row['Field'] ?? ''), $rows),
            true,
        );
    } catch (Throwable) {
        return $cache[$table] = [];
    }
}

function api_student_subject_authorized_row(PDO $pdo, int $studentId, int $subjectVersionId): array
{
    foreach (api_student_home_curriculum_subjects($pdo, $studentId, 100) as $row) {
        if ((int)($row['subject_version_id'] ?? 0) === $subjectVersionId) {
            return $row;
        }
    }
    api_error('subject_not_found', 'المادة غير متاحة لهذا الحساب.', 404);
}

function api_student_subject_identity(PDO $pdo, int $subjectVersionId, array $authorized): array
{
    $versionColumns = api_student_subject_columns($pdo, 'subject_versions');
    $subjectColumns = api_student_subject_columns($pdo, 'subjects');
    if (!isset($versionColumns['id'], $versionColumns['subject_id'], $subjectColumns['id'])) {
        return [
            'name' => trim((string)($authorized['name'] ?? '')),
            'curriculum_label' => '',
            'version_type' => '',
            'structure_mode' => 'unknown',
            'has_parts' => false,
        ];
    }

    $select = ['s.name AS subject_name'];
    $select[] = isset($versionColumns['version_name']) ? 'sv.version_name' : "'' AS version_name";
    $select[] = isset($versionColumns['version_type']) ? 'sv.version_type' : "'' AS version_type";
    $select[] = isset($versionColumns['structure_mode']) ? 'sv.structure_mode' : "'' AS structure_mode";
    if (isset($versionColumns['has_two_parts'])) {
        $select[] = 'sv.has_two_parts';
    } elseif (isset($subjectColumns['has_two_parts'])) {
        $select[] = 's.has_two_parts';
    } else {
        $select[] = '0 AS has_two_parts';
    }

    try {
        $statement = $pdo->prepare(
            'SELECT ' . implode(',', $select)
            . ' FROM subject_versions sv JOIN subjects s ON s.id=sv.subject_id'
            . ' WHERE sv.id=? LIMIT 1'
        );
        $statement->execute([$subjectVersionId]);
        $row = $statement->fetch(PDO::FETCH_ASSOC) ?: [];
    } catch (Throwable) {
        $row = [];
    }

    $structureMode = strtolower(trim((string)($row['structure_mode'] ?? '')));
    if (!in_array($structureMode, ['units', 'lessons'], true)) {
        $structureMode = 'unknown';
    }
    return [
        'name' => trim((string)($row['subject_name'] ?? $authorized['name'] ?? '')),
        'curriculum_label' => trim((string)($row['version_name'] ?? '')),
        'version_type' => trim((string)($row['version_type'] ?? '')),
        'structure_mode' => $structureMode,
        'has_parts' => (int)($row['has_two_parts'] ?? 0) === 1,
    ];
}

function api_student_subject_level_state(float $xp): array
{
    $xp = max(0.0, $xp);
    if (function_exists('calc_level')) {
        $state = (array)calc_level($xp, 100);
        $level = max(1, min(10, (int)($state['level'] ?? 1)));
        $progress = max(0.0, min(1.0, (float)($state['progress'] ?? 0.0)));
        return [
            'level' => $level,
            'progress_percent' => $level >= 10 ? 100 : (int)round($progress * 100),
        ];
    }

    // Compatibility fallback mirrors the current platform rule.
    $level = max(1, min(10, (int)floor($xp / 100.0) + 1));
    $base = ($level - 1) * 100.0;
    $progress = $level >= 10 ? 1.0 : max(0.0, min(1.0, ($xp - $base) / 100.0));
    return [
        'level' => $level,
        'progress_percent' => (int)round($progress * 100),
    ];
}

function api_student_subject_state(PDO $pdo, int $studentId, int $subjectVersionId, array $authorized): array
{
    $columns = api_student_subject_columns($pdo, 'student_subject_state');
    $currentHearts = max(0, min(3, (int)($authorized['hearts'] ?? 3)));
    $points = null;
    $pointsAvailable = false;
    $refillDate = '';

    if (isset($columns['student_id'], $columns['subject_version_id'])) {
        $select = [];
        foreach (['subject_xp', 'hearts', 'hearts_refill_date'] as $column) {
            if (isset($columns[$column])) {
                $select[] = $column;
            }
        }
        if ($select !== []) {
            try {
                $statement = $pdo->prepare(
                    'SELECT ' . implode(',', $select)
                    . ' FROM student_subject_state WHERE student_id=? AND subject_version_id=? LIMIT 1'
                );
                $statement->execute([$studentId, $subjectVersionId]);
                $row = $statement->fetch(PDO::FETCH_ASSOC) ?: [];
                if (array_key_exists('subject_xp', $row) && is_numeric($row['subject_xp'])) {
                    $points = max(0, (int)round((float)$row['subject_xp']));
                    $pointsAvailable = true;
                }
                if (array_key_exists('hearts', $row) && is_numeric($row['hearts'])) {
                    $currentHearts = max(0, min(3, (int)$row['hearts']));
                }
                $refillDate = trim((string)($row['hearts_refill_date'] ?? ''));
            } catch (Throwable) {
                // The authorized list values remain safe fallbacks.
            }
        }
    }

    // Platform hearts refill is daily. This endpoint remains read-only and only
    // projects the effective balance instead of mutating student_subject_state.
    $today = (new DateTimeImmutable('now', new DateTimeZone('Asia/Aden')))->format('Y-m-d');
    if ($refillDate !== '' && $refillDate !== $today) {
        $currentHearts = 3;
    }

    $levelState = $pointsAvailable
        ? api_student_subject_level_state((float)$points)
        : ['level' => null, 'progress_percent' => null];

    return [
        'points' => [
            'available' => $pointsAvailable,
            'value' => $points,
            'reason' => $pointsAvailable ? '' : 'لم يتوفر سجل نقاط مؤكد لهذه المادة.',
        ],
        'level' => [
            'available' => $pointsAvailable,
            'value' => $pointsAvailable ? (int)$levelState['level'] : null,
            'reason' => $pointsAvailable ? '' : 'لم يتوفر سجل نقاط مؤكد لحساب مستوى المادة.',
        ],
        'progress' => [
            'available' => $pointsAvailable,
            'percent' => $pointsAvailable ? (int)$levelState['progress_percent'] : null,
            'reason' => $pointsAvailable ? '' : 'لم يتوفر سجل نقاط مؤكد لحساب تقدم المستوى.',
        ],
        'hearts' => [
            'current' => $currentHearts,
            'maximum' => 3,
            'next_restore' => [
                'available' => false,
                'at' => null,
                'reason' => $currentHearts >= 3
                    ? 'القلوب مكتملة. نظام المنصة يعيد القلوب يوميًا إلى 3 عند بداية يوم جديد.'
                    : 'يعيد نظام المنصة القلوب يوميًا إلى 3 عند بداية يوم جديد.',
            ],
        ],
    ];
}

function api_student_subject_parts(PDO $pdo, int $subjectVersionId, bool $hasParts): array
{
    $parts = [];
    foreach ([['units', 'units_count'], ['lessons', 'lessons_count']] as [$table, $countKey]) {
        $columns = api_student_subject_columns($pdo, $table);
        if (!isset($columns['subject_version_id'])) {
            continue;
        }
        $partExpression = isset($columns['part']) ? 'COALESCE(part,0)' : '0';
        $activeFilter = isset($columns['is_active']) ? ' AND is_active=1' : '';
        try {
            $statement = $pdo->prepare(
                "SELECT {$partExpression} AS part_number, COUNT(*) AS item_count"
                . " FROM {$table} WHERE subject_version_id=?{$activeFilter}"
                . " GROUP BY {$partExpression} ORDER BY {$partExpression} ASC"
            );
            $statement->execute([$subjectVersionId]);
            foreach ($statement->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
                $part = max(0, (int)($row['part_number'] ?? 0));
                $parts[$part] ??= [
                    'part_number' => $part,
                    'label' => $part > 0 ? 'الجزء ' . $part : 'المحتوى',
                    'units_count' => 0,
                    'lessons_count' => 0,
                ];
                $parts[$part][$countKey] = max(0, (int)($row['item_count'] ?? 0));
            }
        } catch (Throwable) {
            // A partial content summary is preferable to inventing counts.
        }
    }
    if ($parts === [] && $hasParts) {
        return [];
    }
    ksort($parts, SORT_NUMERIC);
    return array_values($parts);
}

function api_student_subject_order_column(array $columns): string
{
    foreach (['sort_order', 'position', 'order_index', 'display_order', 'sequence', 'id'] as $column) {
        if (isset($columns[$column])) {
            return $column;
        }
    }
    return '';
}

function api_student_subject_content_label(array $row, string $fallback): string
{
    foreach (['title', 'name', 'unit_name', 'lesson_name', 'display_name'] as $key) {
        $value = trim((string)($row[$key] ?? ''));
        if ($value !== '') {
            return $value;
        }
    }
    return $fallback;
}

function api_student_subject_learning_state(bool $active, bool $inProgress): array
{
    if (!$active) {
        return [
            'status' => 'unavailable',
            'reason' => 'هذا المحتوى غير منشور للطالب.',
        ];
    }
    if ($inProgress) {
        return [
            'status' => 'in_progress',
            'reason' => '',
        ];
    }
    return [
        'status' => 'unknown',
        'reason' => 'لم يُثبت بعد مصدر خادمي موحد لحالة الفتح أو الإكمال لهذا المحتوى.',
    ];
}

function api_student_subject_content_details(
    PDO $pdo,
    int $studentId,
    int $subjectVersionId,
    array $identity,
    array $lastActivity,
): array {
    $unitColumns = api_student_subject_columns($pdo, 'units');
    $lessonColumns = api_student_subject_columns($pdo, 'lessons');
    $canReadUnits = isset($unitColumns['id'], $unitColumns['subject_version_id']);
    $canReadLessons = isset($lessonColumns['id'])
        && (isset($lessonColumns['subject_version_id']) || isset($lessonColumns['unit_id']));

    if (!$canReadUnits && !$canReadLessons) {
        return [
            'details_available' => false,
            'units' => [],
            'lessons' => [],
            'progress_settings' => [
                'progress_mode' => 'unit',
                'unlock_mode' => 'sequential',
                'unlock_threshold_percent' => 30.0,
                'review_progress_cap_points' => 150.0,
            ],
            'reason' => 'جداول الوحدات والدروس غير متاحة بعقد يمكن قراءته بأمان.',
        ];
    }

    $units = [];
    $unitIndex = [];
    $unitParts = [];
    if ($canReadUnits) {
        $where = ['subject_version_id=?'];
        if (isset($unitColumns['is_active'])) {
            $where[] = 'is_active=1';
        }
        $orderColumn = api_student_subject_order_column($unitColumns);
        $orderBy = $orderColumn !== '' ? " ORDER BY `{$orderColumn}` ASC, id ASC" : ' ORDER BY id ASC';
        try {
            $statement = $pdo->prepare(
                'SELECT * FROM units WHERE ' . implode(' AND ', $where) . $orderBy
            );
            $statement->execute([$subjectVersionId]);
            foreach ($statement->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
                $unitId = max(0, (int)($row['id'] ?? 0));
                if ($unitId <= 0) {
                    continue;
                }
                $part = isset($unitColumns['part']) ? max(0, (int)($row['part'] ?? 0)) : 0;
                $active = !isset($unitColumns['is_active']) || (int)($row['is_active'] ?? 0) === 1;
                $state = api_student_subject_learning_state(
                    $active,
                    $unitId === (int)($lastActivity['unit_id'] ?? 0),
                );
                $unitIndex[$unitId] = count($units);
                $unitParts[$unitId] = $part;
                $units[] = [
                    'id' => $unitId,
                    'part_number' => $part,
                    'title' => api_student_subject_content_label($row, 'وحدة ' . $unitId),
                    'position' => $orderColumn !== '' ? max(0, (int)($row[$orderColumn] ?? 0)) : $unitId,
                    'state' => $state,
                    'lessons' => [],
                ];
            }
        } catch (Throwable) {
            $units = [];
            $unitIndex = [];
            $unitParts = [];
        }
    }

    $standaloneLessons = [];
    if ($canReadLessons) {
        $params = [$subjectVersionId];
        $where = [];
        $joins = '';
        if (isset($lessonColumns['subject_version_id'])) {
            $where[] = 'l.subject_version_id=?';
        } elseif (isset($lessonColumns['unit_id'])) {
            $joins = ' JOIN units u ON u.id=l.unit_id';
            $where[] = 'u.subject_version_id=?';
            if (isset($unitColumns['is_active'])) {
                $where[] = 'u.is_active=1';
            }
        }
        if (isset($lessonColumns['is_active'])) {
            $where[] = 'l.is_active=1';
        }
        $lessonOrderColumn = api_student_subject_order_column($lessonColumns);
        $orderParts = [];
        if (isset($lessonColumns['unit_id'])) {
            $orderParts[] = 'l.unit_id ASC';
        }
        $orderParts[] = $lessonOrderColumn !== '' ? "l.`{$lessonOrderColumn}` ASC" : 'l.id ASC';
        if ($lessonOrderColumn !== 'id') {
            $orderParts[] = 'l.id ASC';
        }

        try {
            $statement = $pdo->prepare(
                'SELECT l.* FROM lessons l' . $joins
                . ' WHERE ' . implode(' AND ', $where)
                . ' ORDER BY ' . implode(', ', $orderParts)
            );
            $statement->execute($params);
            foreach ($statement->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
                $lessonId = max(0, (int)($row['id'] ?? 0));
                if ($lessonId <= 0) {
                    continue;
                }
                $unitId = isset($lessonColumns['unit_id']) ? max(0, (int)($row['unit_id'] ?? 0)) : 0;
                if ($unitId > 0 && $canReadUnits && !isset($unitIndex[$unitId])) {
                    // Never surface a lesson whose parent unit is outside the published subject tree.
                    continue;
                }
                $part = isset($lessonColumns['part'])
                    ? max(0, (int)($row['part'] ?? 0))
                    : (int)($unitParts[$unitId] ?? 0);
                $active = !isset($lessonColumns['is_active']) || (int)($row['is_active'] ?? 0) === 1;
                $state = api_student_subject_learning_state(
                    $active,
                    $lessonId === (int)($lastActivity['lesson_id'] ?? 0),
                );
                $lesson = [
                    'id' => $lessonId,
                    'unit_id' => $unitId > 0 ? $unitId : null,
                    'part_number' => $part,
                    'title' => api_student_subject_content_label($row, 'درس ' . $lessonId),
                    'position' => $lessonOrderColumn !== ''
                        ? max(0, (int)($row[$lessonOrderColumn] ?? 0))
                        : $lessonId,
                    '_content_node_id' => isset($lessonColumns['content_node_id'])
                        ? max(0, (int)($row['content_node_id'] ?? 0))
                        : 0,
                    'state' => $state,
                    'preparation' => [
                        'available' => false,
                        'reason' => 'سيُفعّل بدء الدرس بعد تثبيت مصدر قواعد الفتح الخادمية.',
                    ],
                ];
                if ($unitId > 0 && isset($unitIndex[$unitId])) {
                    $units[$unitIndex[$unitId]]['lessons'][] = $lesson;
                } else {
                    $standaloneLessons[] = $lesson;
                }
            }
        } catch (Throwable) {
            $standaloneLessons = [];
            foreach ($units as $index => $unit) {
                $units[$index]['lessons'] = [];
            }
        }
    }

    $hasContent = $units !== [] || $standaloneLessons !== [];
    $projected = api_student_subject_apply_progress_states(
        $pdo,
        $studentId,
        $subjectVersionId,
        $units,
        $standaloneLessons,
        $lastActivity,
    );
    return [
        'details_available' => true,
        'units' => $projected['units'],
        'lessons' => $projected['lessons'],
        'progress_settings' => $projected['settings'],
        'reason' => $hasContent ? '' : 'لا توجد وحدات أو دروس منشورة لهذه المادة حاليًا.',
    ];
}

function api_student_subject_last_activity(PDO $pdo, int $studentId, int $subjectVersionId): array
{
    $columns = api_student_subject_columns($pdo, 'student_last_activity');
    if (!isset($columns['student_id'], $columns['subject_version_id'])) {
        return [
            'available' => false,
            'unit_id' => null,
            'lesson_id' => null,
            'mode' => '',
            'updated_at' => '',
            'preparation' => ['available' => false, 'reason' => 'مصدر النشاط السابق غير متاح.'],
            'reason' => 'لا يوجد نشاط سابق مؤكد لهذه المادة.',
        ];
    }
    $select = [];
    foreach (['unit_id', 'lesson_id', 'mode', 'updated_at'] as $column) {
        if (isset($columns[$column])) {
            $select[] = $column;
        }
    }
    $orderBy = isset($columns['updated_at'])
        ? ' ORDER BY updated_at DESC'
        : (isset($columns['id']) ? ' ORDER BY id DESC' : '');
    try {
        $statement = $pdo->prepare(
            'SELECT ' . ($select !== [] ? implode(',', $select) : 'subject_version_id')
            . ' FROM student_last_activity WHERE student_id=? AND subject_version_id=?'
            . $orderBy
            . ' LIMIT 1'
        );
        $statement->execute([$studentId, $subjectVersionId]);
        $row = $statement->fetch(PDO::FETCH_ASSOC) ?: null;
    } catch (Throwable) {
        $row = null;
    }
    if (!$row) {
        return [
            'available' => false,
            'unit_id' => null,
            'lesson_id' => null,
            'mode' => '',
            'updated_at' => '',
            'preparation' => ['available' => false, 'reason' => 'لا يوجد عقد نشاط سابق مكتمل.'],
            'reason' => 'لا يوجد نشاط سابق مؤكد لهذه المادة.',
        ];
    }
    $unitId = max(0, (int)($row['unit_id'] ?? 0));
    $lessonId = max(0, (int)($row['lesson_id'] ?? 0));
    return [
        'available' => true,
        'unit_id' => $unitId > 0 ? $unitId : null,
        'lesson_id' => $lessonId > 0 ? $lessonId : null,
        'mode' => trim((string)($row['mode'] ?? '')),
        'updated_at' => trim((string)($row['updated_at'] ?? '')),
        'preparation' => [
            'available' => false,
            'reason' => 'السجل الحالي لا يحتوي نوع نشاط ومصدرًا كافيين لبناء شاشة التهيئة بأمان.',
        ],
        'reason' => '',
    ];
}

function api_student_subject_payload(PDO $pdo, array $session, int $subjectVersionId): array
{
    $studentId = (int)($session['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }
    if ($subjectVersionId <= 0) {
        api_error('invalid_subject', 'معرف المادة غير صالح.', 422);
    }

    $authorized = api_student_subject_authorized_row($pdo, $studentId, $subjectVersionId);
    $identity = api_student_subject_identity($pdo, $subjectVersionId, $authorized);
    if ($identity['name'] === '') {
        api_error('subject_not_found', 'المادة غير متاحة لهذا الحساب.', 404);
    }
    $state = api_student_subject_state($pdo, $studentId, $subjectVersionId, $authorized);
    $parts = api_student_subject_parts($pdo, $subjectVersionId, (bool)$identity['has_parts']);
    $lastActivity = api_student_subject_last_activity($pdo, $studentId, $subjectVersionId);
    $contentDetails = api_student_subject_content_details($pdo, $studentId, $subjectVersionId, $identity, $lastActivity);
    $academic = api_student_subjects_academic_context($pdo, $studentId);
    $curriculumLabel = $identity['curriculum_label'] !== ''
        ? $identity['curriculum_label']
        : trim((string)($academic['curriculum_name'] ?? ''));

    $data = [
        'student_id' => (string)$studentId,
        'subject_version_id' => $subjectVersionId,
        'generated_at' => gmdate(DATE_ATOM),
        'identity' => [
            'name' => $identity['name'],
            'curriculum_label' => $curriculumLabel,
            'version_type' => $identity['version_type'],
            'media' => [
                'available' => false,
                'key' => null,
                'reason' => 'لم يُربط مفتاح وسائط دلالي مؤكد بهذه المادة بعد.',
            ],
        ],
        'points' => $state['points'],
        'level' => $state['level'],
        'progress' => $state['progress'],
        'hearts' => $state['hearts'],
        'access' => [
            'available' => false,
            'status' => 'unknown',
            'reason' => 'ارتباط المادة بالسياق مؤكد، أما سياسة الاشتراك التفصيلية فليست مربوطة بهذه الخدمة بعد.',
        ],
        'content' => [
            'structure_mode' => $identity['structure_mode'],
            'has_parts' => (bool)$identity['has_parts'] || count($parts) > 1,
            'parts' => $parts,
            'details_available' => (bool)$contentDetails['details_available'],
            'units' => $contentDetails['units'],
            'lessons' => $contentDetails['lessons'],
            'progress_settings' => $contentDetails['progress_settings'],
            'reason' => $contentDetails['reason'],
        ],
        'last_activity' => $lastActivity,
        'actions' => [
            'training_center' => [
                'available' => false,
                'reason' => 'مركز تدريب المادة سيُفعّل في المرحلة الحادية عشرة.',
            ],
        ],
    ];
    $data['version'] = hash(
        'sha256',
        json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
    );
    return $data;
}
