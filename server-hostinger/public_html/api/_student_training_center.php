<?php
declare(strict_types=1);

const API_TRAINING_CENTER_TOOL_DEFINITIONS = [
    [
        'key' => 'choose',
        'title' => 'الاختيار من متعدد',
        'description' => 'اختر الإجابة الصحيحة من الخيارات المتاحة.',
        'activity_type' => 'choose_test',
        'activity_mode' => 'practice',
        'source' => 'subject',
        'tables' => [
            ['table' => 'choose_questions'],
            ['table' => 'questions_choose'],
            ['table' => 'mcq_questions'],
            ['table' => 'questions', 'types' => ['choose', 'mcq', 'multiple_choice', 'choice']],
            ['table' => 'unit_questions', 'types' => ['choose', 'mcq', 'multiple_choice', 'choice']],
        ],
    ],
    [
        'key' => 'truefalse',
        'title' => 'صح أو خطأ',
        'description' => 'حدّد صحة العبارة بسرعة ودقة.',
        'activity_type' => 'true_false_test',
        'activity_mode' => 'practice',
        'source' => 'subject',
        'tables' => [
            ['table' => 'truefalse_questions'],
            ['table' => 'true_false_questions'],
            ['table' => 'questions_truefalse'],
            ['table' => 'questions', 'types' => ['truefalse', 'true_false', 'tf']],
            ['table' => 'unit_questions', 'types' => ['truefalse', 'true_false', 'tf']],
        ],
    ],
    [
        'key' => 'connect',
        'title' => 'التوصيل',
        'description' => 'اربط كل عنصر بالإجابة المناسبة.',
        'activity_type' => 'connect_test',
        'activity_mode' => 'practice',
        'source' => 'subject',
        'tables' => [
            ['table' => 'connect_questions'],
            ['table' => 'match_questions'],
            ['table' => 'questions_connect'],
            ['table' => 'questions', 'types' => ['connect', 'match', 'matching']],
            ['table' => 'unit_questions', 'types' => ['connect', 'match', 'matching']],
        ],
    ],
    [
        'key' => 'fill',
        'title' => 'الإكمال',
        'description' => 'أكمل العبارة بالمعلومة الصحيحة.',
        'activity_type' => 'fill_test',
        'activity_mode' => 'practice',
        'source' => 'subject',
        'tables' => [
            ['table' => 'fill_questions'],
            ['table' => 'fill_blank_questions'],
            ['table' => 'questions_fill'],
            ['table' => 'questions', 'types' => ['fill', 'fill_blank', 'completion']],
            ['table' => 'unit_questions', 'types' => ['fill', 'fill_blank', 'completion']],
        ],
    ],
    [
        'key' => 'speed',
        'title' => 'السرعة',
        'description' => 'تدرّب على الإجابة الصحيحة خلال وقت قصير.',
        'activity_type' => 'speed_test',
        'activity_mode' => 'speed',
        'source' => 'subject',
        'tables' => [
            ['table' => 'speed_questions'],
            ['table' => 'questions_speed'],
            ['table' => 'questions', 'types' => ['speed', 'speed_test', 'mcq', 'choose', 'multiple_choice', 'choice']],
            ['table' => 'unit_questions', 'types' => ['speed', 'speed_test']],
        ],
    ],
    [
        'key' => 'smart_review',
        'title' => 'راجع أخطاءك بذكاء',
        'description' => 'تدرّب على الأخطاء المؤكدة في محاولاتك السابقة.',
        'activity_type' => 'smart_review',
        'activity_mode' => 'review',
        'source' => 'review',
        'review' => true,
    ],
];

function api_training_center_allowed_tables(): array
{
    static $tables = null;
    if ($tables !== null) {
        return $tables;
    }
    $tables = [
        'choose_questions',
        'questions_choose',
        'mcq_questions',
        'truefalse_questions',
        'true_false_questions',
        'questions_truefalse',
        'connect_questions',
        'match_questions',
        'questions_connect',
        'fill_questions',
        'fill_blank_questions',
        'questions_fill',
        'speed_questions',
        'questions_speed',
        'questions',
        'unit_questions',
        // Production question schema stores type-specific data in normalized child tables.
        'question_mcq_options',
        'question_tf',
        'question_match_pairs',
        'question_fill',
        'question_fill_answers',
        'student_question_errors',
        'student_errors',
        'student_wrong_answers',
        'wrong_answers',
        'units',
        'lessons',
    ];
    return $tables;
}

function api_training_center_columns(PDO $pdo, string $table): array
{
    static $cache = [];
    if (!in_array($table, api_training_center_allowed_tables(), true)) {
        return [];
    }
    if (array_key_exists($table, $cache)) {
        return $cache[$table];
    }
    try {
        $rows = $pdo->query("SHOW COLUMNS FROM `{$table}`")->fetchAll(PDO::FETCH_ASSOC) ?: [];
        return $cache[$table] = array_fill_keys(
            array_filter(array_map(static fn(array $row): string => (string)($row['Field'] ?? ''), $rows)),
            true,
        );
    } catch (Throwable) {
        return $cache[$table] = [];
    }
}

function api_training_center_first_column(array $columns, array $candidates): ?string
{
    foreach ($candidates as $candidate) {
        if (isset($columns[$candidate])) {
            return $candidate;
        }
    }
    return null;
}

function api_training_center_relation(array $columns, string $alias = 'q'): ?array
{
    if (isset($columns['subject_version_id'])) {
        return ['joins' => '', 'where' => "{$alias}.subject_version_id=?"];
    }
    if (isset($columns['unit_id'])) {
        return [
            'joins' => " JOIN units tc_u ON tc_u.id={$alias}.unit_id",
            'where' => 'tc_u.subject_version_id=?',
        ];
    }
    if (isset($columns['lesson_id'])) {
        return [
            'joins' => " JOIN lessons tc_l ON tc_l.id={$alias}.lesson_id"
                . ' JOIN units tc_u ON tc_u.id=tc_l.unit_id',
            'where' => 'tc_u.subject_version_id=?',
        ];
    }
    return null;
}

function api_training_center_count_questions(
    PDO $pdo,
    int $subjectVersionId,
    array $candidates,
): ?int {
    foreach ($candidates as $candidate) {
        $table = (string)($candidate['table'] ?? '');
        $columns = api_training_center_columns($pdo, $table);
        if ($columns === []) {
            continue;
        }
        $relation = api_training_center_relation($columns);
        if ($relation === null) {
            continue;
        }

        $where = [$relation['where']];
        $parameters = [$subjectVersionId];
        if (isset($columns['is_active'])) {
            $where[] = 'q.is_active=1';
        }
        if (isset($columns['status'])) {
            $where[] = "LOWER(TRIM(q.status))='active'";
        }

        $types = array_values(array_filter((array)($candidate['types'] ?? []), 'is_string'));
        if ($types !== []) {
            $typeColumn = api_training_center_first_column(
                $columns,
                ['question_type', 'type', 'mode', 'kind'],
            );
            if ($typeColumn === null) {
                continue;
            }
            $where[] = 'LOWER(TRIM(q.`' . $typeColumn . '`)) IN ('
                . implode(',', array_fill(0, count($types), '?')) . ')';
            array_push($parameters, ...array_map('strtolower', $types));
        }

        try {
            $statement = $pdo->prepare(
                'SELECT COUNT(*) FROM `' . $table . '` q'
                . $relation['joins']
                . ' WHERE ' . implode(' AND ', $where),
            );
            $statement->execute($parameters);
            return max(0, (int)$statement->fetchColumn());
        } catch (Throwable) {
            continue;
        }
    }
    return null;
}

function api_training_center_count_review_errors(
    PDO $pdo,
    int $studentId,
    int $subjectVersionId,
): ?int {
    foreach (
        ['student_question_errors', 'student_errors', 'student_wrong_answers', 'wrong_answers']
        as $table
    ) {
        $columns = api_training_center_columns($pdo, $table);
        if ($columns === []) {
            continue;
        }
        $studentColumn = api_training_center_first_column($columns, ['student_id', 'user_id']);
        $relation = api_training_center_relation($columns, 'e');
        if ($studentColumn === null || $relation === null) {
            continue;
        }
        $where = ["e.`{$studentColumn}`=?", $relation['where']];
        if (isset($columns['is_resolved'])) {
            $where[] = 'COALESCE(e.is_resolved,0)=0';
        }
        try {
            $statement = $pdo->prepare(
                'SELECT COUNT(*) FROM `' . $table . '` e'
                . $relation['joins']
                . ' WHERE ' . implode(' AND ', $where),
            );
            $statement->execute([$studentId, $subjectVersionId]);
            return max(0, (int)$statement->fetchColumn());
        } catch (Throwable) {
            continue;
        }
    }
    return null;
}

function api_training_center_tool_payload(
    PDO $pdo,
    int $studentId,
    int $subjectVersionId,
    array $definition,
): array {
    $count = !empty($definition['review'])
        ? api_training_center_count_review_errors($pdo, $studentId, $subjectVersionId)
        : api_training_center_count_questions(
            $pdo,
            $subjectVersionId,
            (array)($definition['tables'] ?? []),
        );

    $sourceAvailable = $count !== null;
    $available = $sourceAvailable && $count > 0;
    $status = !$sourceAvailable ? 'source_unavailable' : ($available ? 'ready' : 'empty');
    $reason = match ($status) {
        'ready' => '',
        'empty' => !empty($definition['review'])
            ? 'لا توجد أخطاء مؤكدة قابلة للمراجعة الآن.'
            : 'لا توجد أسئلة مؤكدة لهذا النوع في المادة الآن.',
        default => !empty($definition['review'])
            ? 'لم يثبت بعد مصدر موحد لأخطاء الطالب في هذه البيئة.'
            : 'لم يثبت بعد مصدر أسئلة هذا النوع في هذه البيئة.',
    };

    return [
        'key' => (string)$definition['key'],
        'title' => (string)$definition['title'],
        'description' => (string)$definition['description'],
        'activity_type' => (string)$definition['activity_type'],
        'activity_mode' => (string)$definition['activity_mode'],
        'source' => (string)$definition['source'],
        'available' => $available,
        'status' => $status,
        'reason' => $reason,
        'item_count' => $sourceAvailable ? $count : null,
    ];
}

function api_student_training_center_payload(PDO $pdo, array $session, int $subjectVersionId): array
{
    require_once __DIR__ . '/_student_subject.php';

    $studentId = (int)($session['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }
    if ($subjectVersionId <= 0) {
        api_error('invalid_subject', 'معرف المادة غير صالح.', 422);
    }

    $authorized = api_student_subject_authorized_row($pdo, $studentId, $subjectVersionId);
    $identity = api_student_subject_identity($pdo, $subjectVersionId, $authorized);
    if (trim((string)($identity['name'] ?? '')) === '') {
        api_error('subject_not_found', 'المادة غير متاحة لهذا الحساب.', 404);
    }

    $tools = array_map(
        static fn(array $definition): array => api_training_center_tool_payload(
            $pdo,
            $studentId,
            $subjectVersionId,
            $definition,
        ),
        API_TRAINING_CENTER_TOOL_DEFINITIONS,
    );

    $data = [
        'student_id' => (string)$studentId,
        'subject_version_id' => $subjectVersionId,
        'generated_at' => gmdate(DATE_ATOM),
        'identity' => [
            'name' => trim((string)$identity['name']),
            'curriculum_label' => trim((string)($identity['curriculum_label'] ?? '')),
        ],
        'tools' => $tools,
    ];
    $data['version'] = hash(
        'sha256',
        json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
    );
    return $data;
}
