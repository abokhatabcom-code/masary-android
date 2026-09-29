<?php
declare(strict_types=1);

require_once __DIR__ . '/_student_training_center.php';
require_once __DIR__ . '/_activity_preparation.php';
require_once __DIR__ . '/_activity_preparation_lifecycle.php';

const API_QUESTION_SESSION_MAX_QUESTIONS = 60;

function api_question_session_public_id(string $value): string
{
    $value = trim($value);
    if (!preg_match('/^[A-Za-z0-9._:-]{8,128}$/', $value)) {
        api_error('invalid_session', 'معرف جلسة النشاط غير صالح.', 422);
    }
    return $value;
}

function api_question_session_owned_row(PDO $pdo, int $studentId, string $sessionId): array
{
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }
    if (!api_activity_table_exists($pdo, 'api_activity_sessions')) {
        api_error('activity_schema_missing', 'خدمة جلسات النشاط غير متاحة في هذه البيئة.', 503);
    }

    $statement = $pdo->prepare(
        'SELECT * FROM api_activity_sessions WHERE user_id=? AND public_session_id=? LIMIT 1',
    );
    $statement->execute([$studentId, api_question_session_public_id($sessionId)]);
    $row = $statement->fetch(PDO::FETCH_ASSOC) ?: null;
    if (!$row) {
        // Keep ownership and existence indistinguishable.
        api_error('activity_session_not_found', 'جلسة النشاط غير متاحة.', 404);
    }
    api_activity_reject_expired_or_abandoned($pdo, $row);
    return $row;
}

function api_question_session_columns(PDO $pdo, string $table): array
{
    if (!in_array($table, api_training_center_allowed_tables(), true)) {
        return [];
    }

    static $cache = [];
    $driver = strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME));
    $cacheKey = $driver . ':' . $table;
    if (array_key_exists($cacheKey, $cache)) {
        return $cache[$cacheKey];
    }

    try {
        if ($driver === 'sqlite') {
            $rows = $pdo->query("PRAGMA table_info(`{$table}`)")->fetchAll(PDO::FETCH_ASSOC) ?: [];
            return $cache[$cacheKey] = array_fill_keys(
                array_filter(array_map(static fn(array $row): string => (string)($row['name'] ?? ''), $rows)),
                true,
            );
        }

        $rows = $pdo->query("SHOW COLUMNS FROM `{$table}`")->fetchAll(PDO::FETCH_ASSOC) ?: [];
        return $cache[$cacheKey] = array_fill_keys(
            array_filter(array_map(static fn(array $row): string => (string)($row['Field'] ?? ''), $rows)),
            true,
        );
    } catch (Throwable) {
        return $cache[$cacheKey] = [];
    }
}

function api_question_session_first_column(array $columns, array $candidates): ?string
{
    foreach ($candidates as $candidate) {
        if (isset($columns[$candidate])) {
            return $candidate;
        }
    }
    return null;
}

function api_question_session_tool_definition(string $activityType): ?array
{
    foreach (API_TRAINING_CENTER_TOOL_DEFINITIONS as $definition) {
        if ((string)($definition['activity_type'] ?? '') === $activityType) {
            return $definition;
        }
    }
    return null;
}

function api_question_session_question_type(string $activityType): ?string
{
    return match ($activityType) {
        'choose_test' => 'choose',
        'true_false_test' => 'truefalse',
        'connect_test' => 'connect',
        'fill_test' => 'fill',
        'speed_test' => 'speed',
        default => null,
    };
}

function api_question_session_default_test_settings(): array
{
    return api_test_policy_defaults();
}

function api_question_session_merge_test_settings(array $base, array $row): array
{
    return api_test_policy_merge($base, $row);
}

function api_question_session_test_setting_row(
    PDO $pdo,
    string $table,
    string $keyColumn,
    int $keyValue,
): array {
    return api_test_policy_row($pdo, $table, $keyColumn, $keyValue);
}

function api_question_session_lesson_settings(PDO $pdo, array $session): array
{
    return api_test_policy_for_session($pdo, $session);
}

function api_question_session_lesson_allowed_question_types(array $settings): array
{
    $map = [
        'mcq' => 'choose',
        'tf' => 'truefalse',
        'fill' => 'fill',
        'direct' => 'direct',
        'match' => 'connect',
    ];
    $allowed = [];
    foreach ((array)($settings['allowed_types'] ?? []) as $adminType) {
        $wireType = $map[strtolower(trim((string)$adminType))] ?? null;
        if ($wireType !== null && !in_array($wireType, $allowed, true)) {
            $allowed[] = $wireType;
        }
    }
    return $allowed;
}

function api_question_session_stable_sort_key(
    string $sessionId,
    string $scope,
    string $value,
): string {
    return hash_hmac('sha256', $sessionId . '|' . $scope . '|' . $value, api_server_secret());
}

function api_question_session_opaque_id(
    string $sessionId,
    string $table,
    string $rowId,
    string $slot = 'question',
): string {
    return substr(
        hash_hmac('sha256', $sessionId . '|' . $table . '|' . $rowId . '|' . $slot, api_server_secret()),
        0,
        32,
    );
}

function api_question_session_scope(
    array $session,
    array $columns,
    string $alias = 'q',
): ?array {
    $lessonId = (int)($session['lesson_id'] ?? 0);
    $unitId = (int)($session['unit_id'] ?? 0);
    $subjectVersionId = (int)($session['subject_version_id'] ?? 0);

    if ($lessonId > 0 && isset($columns['lesson_id'])) {
        return ['joins' => '', 'where' => "{$alias}.lesson_id=?", 'params' => [$lessonId]];
    }
    if ($unitId > 0 && isset($columns['unit_id'])) {
        return ['joins' => '', 'where' => "{$alias}.unit_id=?", 'params' => [$unitId]];
    }
    if ($subjectVersionId > 0 && isset($columns['subject_version_id'])) {
        return [
            'joins' => '',
            'where' => "{$alias}.subject_version_id=?",
            'params' => [$subjectVersionId],
        ];
    }

    if ($subjectVersionId > 0) {
        $relation = api_training_center_relation($columns, $alias);
        if ($relation !== null) {
            return [
                'joins' => (string)$relation['joins'],
                'where' => (string)$relation['where'],
                'params' => [$subjectVersionId],
            ];
        }
    }
    return null;
}

function api_question_session_rows(
    PDO $pdo,
    array $session,
    array $candidate,
): ?array {
    $table = (string)($candidate['table'] ?? '');
    $columns = api_question_session_columns($pdo, $table);
    if ($columns === []) {
        return null;
    }

    $idColumn = api_question_session_first_column($columns, ['id', 'question_id']);
    $promptColumn = api_question_session_first_column(
        $columns,
        ['question_text', 'question', 'prompt', 'statement', 'text', 'title'],
    );
    $scope = api_question_session_scope($session, $columns);
    if ($idColumn === null || $promptColumn === null || $scope === null) {
        return null;
    }

    $where = [$scope['where']];
    $params = $scope['params'];
    if (isset($columns['is_active'])) {
        $where[] = 'q.is_active=1';
    }
    if (isset($columns['status'])) {
        $where[] = "LOWER(TRIM(q.status))='active'";
    }

    $types = array_values(array_filter((array)($candidate['types'] ?? []), 'is_string'));
    if ($types !== []) {
        $typeColumn = api_question_session_first_column(
            $columns,
            ['question_type', 'type', 'mode', 'kind'],
        );
        if ($typeColumn === null) {
            return null;
        }
        $where[] = 'LOWER(TRIM(q.`' . $typeColumn . '`)) IN ('
            . implode(',', array_fill(0, count($types), '?')) . ')';
        array_push($params, ...array_map('strtolower', $types));
    }

    $select = [
        "q.`{$idColumn}` AS _id",
        "q.`{$promptColumn}` AS _prompt",
    ];
    foreach ([
        'option_a', 'option_b', 'option_c', 'option_d',
        'choice_a', 'choice_b', 'choice_c', 'choice_d',
        'left_text', 'left_item', 'item_left', 'term',
        'right_text', 'right_item', 'item_right', 'definition',
    ] as $optional) {
        if (isset($columns[$optional])) {
            $select[] = "q.`{$optional}` AS `{$optional}`";
        }
    }

    try {
        $sql = 'SELECT ' . implode(',', $select)
            . ' FROM `' . $table . '` q'
            . $scope['joins']
            . ' WHERE ' . implode(' AND ', $where)
            . " ORDER BY q.`{$idColumn}` ASC LIMIT " . API_QUESTION_SESSION_MAX_QUESTIONS;
        $statement = $pdo->prepare($sql);
        $statement->execute($params);
        return [
            'table' => $table,
            'columns' => $columns,
            'rows' => $statement->fetchAll(PDO::FETCH_ASSOC) ?: [],
        ];
    } catch (Throwable) {
        return null;
    }
}

function api_question_session_options(
    string $sessionId,
    string $table,
    string $rowId,
    array $row,
): array {
    $groups = [
        ['option_a', 'option_b', 'option_c', 'option_d'],
        ['choice_a', 'choice_b', 'choice_c', 'choice_d'],
    ];
    foreach ($groups as $columns) {
        $items = [];
        foreach ($columns as $column) {
            $text = trim((string)($row[$column] ?? ''));
            if ($text === '') {
                continue;
            }
            $items[] = [
                'id' => api_question_session_opaque_id($sessionId, $table, $rowId, $column),
                'text' => $text,
            ];
        }
        if (count($items) >= 2) {
            return $items;
        }
    }
    return [];
}

function api_question_session_connect_payload(
    string $sessionId,
    string $table,
    array $rows,
): ?array {
    $leftCandidates = ['left_text', 'left_item', 'item_left', 'term'];
    $rightCandidates = ['right_text', 'right_item', 'item_right', 'definition'];
    $leftItems = [];
    $rightItems = [];

    foreach ($rows as $row) {
        $rowId = (string)($row['_id'] ?? '');
        $leftColumn = api_question_session_first_column(array_fill_keys(array_keys($row), true), $leftCandidates);
        $rightColumn = api_question_session_first_column(array_fill_keys(array_keys($row), true), $rightCandidates);
        $left = $leftColumn === null ? '' : trim((string)($row[$leftColumn] ?? ''));
        $right = $rightColumn === null ? '' : trim((string)($row[$rightColumn] ?? ''));
        if ($rowId === '' || $left === '' || $right === '') {
            continue;
        }
        $leftItems[] = [
            'id' => api_question_session_opaque_id($sessionId, $table, $rowId, 'left'),
            'text' => $left,
        ];
        $rightItems[] = [
            'id' => api_question_session_opaque_id($sessionId, $table, $rowId, 'right'),
            'text' => $right,
        ];
    }

    if (count($leftItems) < 2 || count($leftItems) !== count($rightItems)) {
        return null;
    }

    // Reverse the right side to ensure source row order never reveals the matching relation.
    $rightItems = array_reverse($rightItems);
    return ['left_items' => $leftItems, 'right_items' => $rightItems];
}


function api_question_session_normalized_types(string $questionType): array
{
    return match ($questionType) {
        'choose' => ['mcq', 'choose', 'multiple_choice', 'choice'],
        'truefalse' => ['tf', 'truefalse', 'true_false'],
        'connect' => ['match', 'matching', 'connect'],
        'fill' => ['fill', 'fill_blank', 'completion'],
        'direct' => ['direct', 'short_answer', 'text'],
        // Production speed tests draw from ordinary MCQ rows and add timing at the client/session layer.
        'speed' => ['mcq', 'choose', 'multiple_choice', 'choice', 'speed', 'speed_test'],
        default => [],
    };
}

function api_question_session_lesson_content_node_id(
    PDO $pdo,
    array $session,
): ?int {
    $lessonId = (int)($session['lesson_id'] ?? 0);
    if ($lessonId <= 0) {
        return null;
    }
    $columns = api_question_session_columns($pdo, 'lessons');
    if (!isset($columns['id'], $columns['content_node_id'])) {
        return null;
    }

    $where = ['id=?'];
    $params = [$lessonId];
    $subjectVersionId = (int)($session['subject_version_id'] ?? 0);
    $unitId = (int)($session['unit_id'] ?? 0);
    if ($subjectVersionId > 0 && isset($columns['subject_version_id'])) {
        $where[] = 'subject_version_id=?';
        $params[] = $subjectVersionId;
    }
    if ($unitId > 0 && isset($columns['unit_id'])) {
        $where[] = 'unit_id=?';
        $params[] = $unitId;
    }
    if (isset($columns['is_active'])) {
        $where[] = 'is_active=1';
    }

    try {
        $statement = $pdo->prepare(
            'SELECT content_node_id FROM lessons WHERE ' . implode(' AND ', $where) . ' LIMIT 1',
        );
        $statement->execute($params);
        $contentNodeId = (int)$statement->fetchColumn();
        return $contentNodeId > 0 ? $contentNodeId : null;
    } catch (Throwable) {
        return null;
    }
}

function api_question_session_normalized_scope(
    PDO $pdo,
    array $session,
    array $columns,
): ?array {
    $lessonId = (int)($session['lesson_id'] ?? 0);
    if ($lessonId > 0) {
        if (isset($columns['lesson_id'])) {
            return ['joins' => '', 'where' => 'q.lesson_id=?', 'params' => [$lessonId]];
        }
        if (isset($columns['content_node_id'])) {
            $contentNodeId = api_question_session_lesson_content_node_id($pdo, $session);
            if ($contentNodeId === null) {
                // Never broaden a lesson practice to the whole unit if its lesson mapping is missing.
                return null;
            }
            return [
                'joins' => '',
                'where' => 'q.content_node_id=?',
                'params' => [$contentNodeId],
            ];
        }
    }
    return api_question_session_scope($session, $columns);
}

function api_question_session_normalized_rows(
    PDO $pdo,
    array $session,
    string $questionType,
    ?array $settings = null,
): ?array {
    $columns = api_question_session_columns($pdo, 'questions');
    foreach (['id', 'type', 'question_text'] as $required) {
        if (!isset($columns[$required])) {
            return null;
        }
    }
    $scope = api_question_session_normalized_scope($pdo, $session, $columns);
    if ($scope === null) {
        return [];
    }

    $types = api_question_session_normalized_types($questionType);
    if ($types === []) {
        return [];
    }

    $where = [$scope['where']];
    $params = $scope['params'];
    $where[] = 'LOWER(TRIM(q.type)) IN ('
        . implode(',', array_fill(0, count($types), '?')) . ')';
    array_push($params, ...array_map('strtolower', $types));
    if (isset($columns['status'])) {
        $where[] = "LOWER(TRIM(q.status))='active'";
    }
    if (isset($columns['is_active'])) {
        $where[] = 'q.is_active=1';
    }
    if ($settings !== null && isset($columns['difficulty'])) {
        $difficulties = array_values(
            array_filter(
                array_map(
                    static fn(mixed $value): string => strtolower(trim((string)$value)),
                    (array)($settings['allowed_difficulties'] ?? []),
                ),
                static fn(string $value): bool => $value !== '',
            ),
        );
        if ($difficulties !== []) {
            $where[] = 'LOWER(TRIM(q.difficulty)) IN ('
                . implode(',', array_fill(0, count($difficulties), '?')) . ')';
            array_push($params, ...$difficulties);
        }
    }

    try {
        $statement = $pdo->prepare(
            'SELECT q.id AS _id,q.question_text AS _prompt,q.type AS _type '
            . 'FROM questions q'
            . $scope['joins']
            . ' WHERE ' . implode(' AND ', $where)
            . ' ORDER BY q.id ASC LIMIT ' . API_QUESTION_SESSION_MAX_QUESTIONS,
        );
        $statement->execute($params);
        return $statement->fetchAll(PDO::FETCH_ASSOC) ?: [];
    } catch (Throwable) {
        return null;
    }
}

function api_question_session_normalized_mcq_options(
    PDO $pdo,
    string $sessionId,
    string $questionId,
    bool $shuffle = false,
): array {
    $columns = api_question_session_columns($pdo, 'question_mcq_options');
    if (!isset($columns['id'], $columns['question_id'], $columns['option_text'])) {
        return [];
    }
    $order = isset($columns['sort_order']) ? 'sort_order ASC,id ASC' : 'id ASC';
    try {
        $statement = $pdo->prepare(
            'SELECT id,option_text FROM question_mcq_options WHERE question_id=? ORDER BY ' . $order,
        );
        $statement->execute([$questionId]);
        $options = [];
        foreach ($statement->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
            $optionId = (string)($row['id'] ?? '');
            $text = trim((string)($row['option_text'] ?? ''));
            if ($optionId === '' || $text === '') {
                continue;
            }
            $options[] = [
                'id' => api_question_session_opaque_id(
                    $sessionId,
                    'questions',
                    $questionId,
                    'mcq-option:' . $optionId,
                ),
                'text' => $text,
            ];
        }
        if (count($options) < 2) {
            return [];
        }
        if ($shuffle) {
            usort(
                $options,
                static fn(array $left, array $right): int =>
                    strcmp(
                        api_question_session_stable_sort_key(
                            $sessionId,
                            'mcq:' . $questionId,
                            (string)$left['id'],
                        ),
                        api_question_session_stable_sort_key(
                            $sessionId,
                            'mcq:' . $questionId,
                            (string)$right['id'],
                        ),
                    ),
            );
        }
        return $options;
    } catch (Throwable) {
        return [];
    }
}

function api_question_session_normalized_tf_payload(
    PDO $pdo,
    string $sessionId,
    string $questionId,
    ?array $settings = null,
): ?array {
    $columns = api_question_session_columns($pdo, 'question_tf');
    if (!isset($columns['question_id'], $columns['correct_value'])) {
        return null;
    }
    try {
        $select = isset($columns['requires_reason'])
            ? 'requires_reason'
            : '0 AS requires_reason';
        $statement = $pdo->prepare(
            'SELECT ' . $select . ' FROM question_tf WHERE question_id=? LIMIT 1',
        );
        $statement->execute([$questionId]);
        $row = $statement->fetch(PDO::FETCH_ASSOC);
        if (!$row) {
            return null;
        }

        $requiresReason = (int)($row['requires_reason'] ?? 0) === 1;
        $reasons = [];
        if ($requiresReason) {
            $reasonColumns = api_question_session_columns($pdo, 'question_tf_reasons');
            if (!isset($reasonColumns['id'], $reasonColumns['question_id'], $reasonColumns['label'])) {
                return null;
            }
            $where = 'question_id=?';
            if (isset($reasonColumns['is_active'])) {
                $where .= ' AND is_active=1';
            }
            $order = isset($reasonColumns['sort_order'])
                ? 'sort_order ASC,id ASC'
                : 'id ASC';
            $reasonStatement = $pdo->prepare(
                'SELECT id,label FROM question_tf_reasons WHERE ' . $where . ' ORDER BY ' . $order,
            );
            $reasonStatement->execute([$questionId]);
            foreach ($reasonStatement->fetchAll(PDO::FETCH_ASSOC) ?: [] as $reason) {
                $id = (string)($reason['id'] ?? '');
                $label = trim((string)($reason['label'] ?? ''));
                if ($id === '' || $label === '') continue;
                $reasons[] = [
                    'id' => api_question_session_opaque_id(
                        $sessionId,
                        'questions',
                        $questionId,
                        'tf-reason:' . $id,
                    ),
                    'text' => $label,
                ];
            }
            if ($reasons === []) {
                return null;
            }
        }

        return [
            'options' => [
                [
                    'id' => api_question_session_opaque_id($sessionId, 'questions', $questionId, 'true'),
                    'text' => 'صح',
                ],
                [
                    'id' => api_question_session_opaque_id($sessionId, 'questions', $questionId, 'false'),
                    'text' => 'خطأ',
                ],
            ],
            'requires_reason' => $requiresReason,
            'reason_only_on_false' =>
                (int)($settings['tf_reason_only_on_false'] ?? 1) === 1,
            'reasons' => $reasons,
        ];
    } catch (Throwable) {
        return null;
    }
}


function api_question_session_normalized_fill_payload(
    PDO $pdo,
    string $questionId,
): ?array {
    $fillColumns = api_question_session_columns($pdo, 'question_fill');
    $answerColumns = api_question_session_columns($pdo, 'question_fill_answers');
    if (
        !isset($fillColumns['question_id'], $fillColumns['blanks_count'])
        || !isset($answerColumns['question_id'], $answerColumns['blank_index'], $answerColumns['answer_text'])
    ) {
        return null;
    }
    try {
        $statement = $pdo->prepare('SELECT blanks_count FROM question_fill WHERE question_id=? LIMIT 1');
        $statement->execute([$questionId]);
        $blanks = max(1, min(4, (int)$statement->fetchColumn()));
        for ($index = 1; $index <= $blanks; $index++) {
            $answer = $pdo->prepare(
                "SELECT COUNT(*) FROM question_fill_answers "
                . "WHERE question_id=? AND blank_index=? AND TRIM(COALESCE(answer_text,''))<>''",
            );
            $answer->execute([$questionId, $index]);
            if ((int)$answer->fetchColumn() <= 0) {
                return null;
            }
        }
        return [
            'input_mode' => 'text',
            'blanks_count' => $blanks,
        ];
    } catch (Throwable) {
        return null;
    }
}

function api_question_session_normalized_direct_ready(PDO $pdo, string $questionId): bool
{
    $columns = api_question_session_columns($pdo, 'question_direct');
    if (!isset($columns['question_id'], $columns['answer_text'])) {
        return false;
    }
    try {
        $statement = $pdo->prepare(
            "SELECT answer_text FROM question_direct "
            . "WHERE question_id=? AND TRIM(COALESCE(answer_text,''))<>'' LIMIT 1",
        );
        $statement->execute([$questionId]);
        return $statement->fetchColumn() !== false;
    } catch (Throwable) {
        return false;
    }
}


function api_question_session_normalized_match_pairs(
    PDO $pdo,
    string $sessionId,
    string $questionId,
    bool $shuffleRight = true,
): ?array {
    $columns = api_question_session_columns($pdo, 'question_match_pairs');
    if (
        !isset($columns['id'], $columns['question_id'], $columns['left_text'], $columns['right_text'])
    ) {
        return null;
    }
    $order = isset($columns['sort_order']) ? 'sort_order ASC,id ASC' : 'id ASC';
    try {
        $statement = $pdo->prepare(
            'SELECT id,left_text,right_text FROM question_match_pairs '
            . 'WHERE question_id=? ORDER BY ' . $order,
        );
        $statement->execute([$questionId]);
        $leftItems = [];
        $rightItems = [];
        foreach ($statement->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
            $pairId = (string)($row['id'] ?? '');
            $left = trim((string)($row['left_text'] ?? ''));
            $right = trim((string)($row['right_text'] ?? ''));
            if ($pairId === '' || $left === '' || $right === '') {
                continue;
            }
            $leftItems[] = [
                'id' => api_question_session_opaque_id(
                    $sessionId,
                    'questions',
                    $questionId,
                    'match-left:' . $pairId,
                ),
                'text' => $left,
            ];
            $rightItems[] = [
                'id' => api_question_session_opaque_id(
                    $sessionId,
                    'questions',
                    $questionId,
                    'match-right:' . $pairId,
                ),
                'text' => $right,
            ];
        }
        if (count($leftItems) < 2 || count($leftItems) !== count($rightItems)) {
            return null;
        }
        if ($shuffleRight) {
            usort(
                $rightItems,
                static fn(array $left, array $right): int =>
                    strcmp(
                        api_question_session_stable_sort_key(
                            $sessionId,
                            'match:' . $questionId,
                            (string)$left['id'],
                        ),
                        api_question_session_stable_sort_key(
                            $sessionId,
                            'match:' . $questionId,
                            (string)$right['id'],
                        ),
                    ),
            );
        }
        return [
            'left_items' => $leftItems,
            'right_items' => $rightItems,
        ];
    } catch (Throwable) {
        return null;
    }
}

function api_question_session_normalized_questions(
    PDO $pdo,
    array $session,
    string $questionType,
    ?array $settings = null,
    bool $includeSourceMeta = false,
): ?array {
    $rows = api_question_session_normalized_rows($pdo, $session, $questionType, $settings);
    if ($rows === null) {
        return null;
    }

    $sessionId = (string)($session['public_session_id'] ?? '');
    $questions = [];
    foreach ($rows as $row) {
        $rowId = (string)($row['_id'] ?? '');
        $prompt = trim((string)($row['_prompt'] ?? ''));
        if ($rowId === '' || $prompt === '') {
            continue;
        }

        $payload = [];
        if (in_array($questionType, ['choose', 'speed'], true)) {
            $options = api_question_session_normalized_mcq_options(
                $pdo,
                $sessionId,
                $rowId,
                (int)($settings['shuffle_mcq_options'] ?? 0) === 1,
            );
            if ($options === []) {
                continue;
            }
            $payload['options'] = $options;
        } elseif ($questionType === 'truefalse') {
            $tfPayload = api_question_session_normalized_tf_payload(
                $pdo,
                $sessionId,
                $rowId,
                $settings,
            );
            if ($tfPayload === null) {
                continue;
            }
            $payload = $tfPayload;
        } elseif ($questionType === 'fill') {
            $fillPayload = api_question_session_normalized_fill_payload($pdo, $rowId);
            if ($fillPayload === null) {
                continue;
            }
            $payload = $fillPayload;
        } elseif ($questionType === 'direct') {
            if (!api_question_session_normalized_direct_ready($pdo, $rowId)) {
                continue;
            }
            $payload['input_mode'] = 'text';
        } elseif ($questionType === 'connect') {
            $matchPayload = api_question_session_normalized_match_pairs(
                $pdo,
                $sessionId,
                $rowId,
                $settings === null
                    || (int)($settings['shuffle_match_right'] ?? 1) === 1,
            );
            if ($matchPayload === null) {
                continue;
            }
            $payload = $matchPayload;
        }

        $question = [
            'id' => api_question_session_opaque_id($sessionId, 'questions', $rowId),
            'type' => $questionType,
            'prompt' => $prompt,
            'payload' => $payload,
        ];
        if ($includeSourceMeta) {
            $question['_source_id'] = (int)$rowId;
        }
        $questions[] = $question;
    }
    return $questions;
}

function api_question_session_lesson_practice_questions(
    PDO $pdo,
    array $session,
): array {
    if ((int)($session['lesson_id'] ?? 0) <= 0) {
        api_error(
            'question_source_unavailable',
            'جلسة تدريب الدرس لا تحتوي معرف درس صالحًا.',
            503,
        );
    }

    $settings = api_question_session_lesson_settings($pdo, $session);
    $allowedQuestionTypes = api_question_session_lesson_allowed_question_types($settings);
    if ($allowedQuestionTypes === []) {
        api_error(
            'question_source_unavailable',
            'إعدادات الاختبار الحالية لا تسمح بأنواع أسئلة يدعمها تطبيق الطالب.',
            503,
        );
    }

    $questions = [];
    foreach ($allowedQuestionTypes as $questionType) {
        $bucket = api_question_session_normalized_questions(
            $pdo,
            $session,
            $questionType,
            $settings,
            true,
        );
        if (is_array($bucket) && $bucket !== []) {
            array_push($questions, ...$bucket);
        }
    }

    if ($questions === []) {
        api_error(
            'question_source_unavailable',
            'لا توجد أسئلة مدعومة ومؤكدة لهذا الدرس وفق إعدادات الاختبار الحالية.',
            503,
        );
    }

    $sessionId = (string)($session['public_session_id'] ?? '');
    if ((string)($settings['question_order'] ?? 'random') === 'fixed') {
        usort(
            $questions,
            static fn(array $left, array $right): int =>
                ((int)($left['_source_id'] ?? 0)) <=> ((int)($right['_source_id'] ?? 0)),
        );
    } else {
        usort(
            $questions,
            static fn(array $left, array $right): int =>
                strcmp(
                    api_question_session_stable_sort_key(
                        $sessionId,
                        'lesson-order',
                        (string)($left['_source_id'] ?? ''),
                    ),
                    api_question_session_stable_sort_key(
                        $sessionId,
                        'lesson-order',
                        (string)($right['_source_id'] ?? ''),
                    ),
                ),
        );
    }

    $limit = max(
        1,
        min(
            API_QUESTION_SESSION_MAX_QUESTIONS,
            (int)($settings['questions_per_attempt'] ?? 10),
        ),
    );
    $questions = array_slice($questions, 0, $limit);
    foreach ($questions as &$question) {
        unset($question['_source_id']);
    }
    unset($question);
    return $questions;
}

function api_question_session_questions(PDO $pdo, array $session): array
{
    $activityType = (string)($session['activity_type'] ?? '');
    if ($activityType === 'lesson_practice') {
        return api_question_session_lesson_practice_questions($pdo, $session);
    }

    $questionType = api_question_session_question_type($activityType);
    $definition = api_question_session_tool_definition($activityType);
    if ($questionType === null || $definition === null || !empty($definition['review'])) {
        api_error(
            'question_source_unavailable',
            'لم يثبت بعد مصدر أسئلة صالح لهذا النشاط.',
            503,
        );
    }

    foreach ((array)($definition['tables'] ?? []) as $candidate) {
        if ((string)($candidate['table'] ?? '') === 'questions') {
            $normalized = api_question_session_normalized_questions($pdo, $session, $questionType);
            if ($normalized !== null && $normalized !== []) {
                return $normalized;
            }
        }

        $source = api_question_session_rows($pdo, $session, (array)$candidate);
        if ($source === null || $source['rows'] === []) {
            continue;
        }

        $table = (string)$source['table'];
        $rows = $source['rows'];
        if ($questionType === 'connect') {
            $payload = api_question_session_connect_payload(
                (string)$session['public_session_id'],
                $table,
                $rows,
            );
            if ($payload === null) {
                continue;
            }
            return [[
                'id' => api_question_session_opaque_id(
                    (string)$session['public_session_id'],
                    $table,
                    'bundle',
                    'connect',
                ),
                'type' => 'connect',
                'prompt' => 'صل كل عنصر بما يناسبه.',
                'payload' => $payload,
            ]];
        }

        $questions = [];
        foreach ($rows as $row) {
            $rowId = (string)($row['_id'] ?? '');
            $prompt = trim((string)($row['_prompt'] ?? ''));
            if ($rowId === '' || $prompt === '') {
                continue;
            }

            $payload = [];
            if (in_array($questionType, ['choose', 'speed'], true)) {
                $options = api_question_session_options(
                    (string)$session['public_session_id'],
                    $table,
                    $rowId,
                    $row,
                );
                if (count($options) < 2) {
                    continue;
                }
                $payload['options'] = $options;
            } elseif ($questionType === 'truefalse') {
                $payload['options'] = [
                    [
                        'id' => api_question_session_opaque_id(
                            (string)$session['public_session_id'],
                            $table,
                            $rowId,
                            'true',
                        ),
                        'text' => 'صح',
                    ],
                    [
                        'id' => api_question_session_opaque_id(
                            (string)$session['public_session_id'],
                            $table,
                            $rowId,
                            'false',
                        ),
                        'text' => 'خطأ',
                    ],
                ];
            } elseif ($questionType === 'fill') {
                $payload['input_mode'] = 'text';
            }

            $questions[] = [
                'id' => api_question_session_opaque_id(
                    (string)$session['public_session_id'],
                    $table,
                    $rowId,
                ),
                'type' => $questionType,
                'prompt' => $prompt,
                'payload' => $payload,
            ];
        }
        if ($questions !== []) {
            return $questions;
        }
    }

    api_error(
        'question_source_unavailable',
        'لا يوجد مصدر أسئلة مدعوم ومؤكد لهذه الجلسة في البيئة الحالية.',
        503,
    );
}

function api_question_session_answered_count(
    PDO $pdo,
    int $studentId,
    string $sessionId,
): int {
    if (!api_activity_table_exists($pdo, 'api_activity_answers')) {
        return 0;
    }
    try {
        $statement = $pdo->prepare(
            'SELECT COUNT(*) FROM api_activity_answers WHERE user_id=? AND public_session_id=?',
        );
        $statement->execute([$studentId, $sessionId]);
        return max(0, (int)$statement->fetchColumn());
    } catch (Throwable) {
        return 0;
    }
}

function api_question_session_ensure_started(PDO $pdo, array $session): array
{
    $startedAt = trim((string)($session['started_at'] ?? ''));
    if ($startedAt !== '') {
        return $session;
    }
    $id = (int)($session['id'] ?? 0);
    if ($id <= 0) {
        return $session;
    }

    $now = gmdate('Y-m-d H:i:s');
    $statement = $pdo->prepare(
        "UPDATE api_activity_sessions SET started_at=?,updated_at=? "
        . "WHERE id=? AND (started_at IS NULL OR started_at='') "
        . "AND status IN ('created','in_progress')",
    );
    $statement->execute([$now, $now, $id]);

    $read = $pdo->prepare('SELECT * FROM api_activity_sessions WHERE id=? LIMIT 1');
    $read->execute([$id]);
    return $read->fetch(PDO::FETCH_ASSOC) ?: $session;
}

function api_question_session_package(PDO $pdo, array $authSession, string $sessionId): array
{
    $studentId = (int)($authSession['user_id'] ?? 0);
    $session = api_question_session_owned_row($pdo, $studentId, $sessionId);
    $session = api_question_session_ensure_started($pdo, $session);
    $questions = api_question_session_questions($pdo, $session);
    $answered = min(
        api_question_session_answered_count($pdo, $studentId, (string)$session['public_session_id']),
        count($questions),
    );

    $data = [
        'generated_at' => gmdate(DATE_ATOM),
        'session' => [
            'id' => (string)$session['public_session_id'],
            'status' => (string)($session['status'] ?? 'created'),
            'expires_at' => (string)($session['expires_at'] ?? ''),
            'started_at' => (string)($session['started_at'] ?? ''),
            'started_at_epoch_seconds' => (($started = strtotime((string)($session['started_at'] ?? ''))) !== false)
                ? (int)$started
                : 0,
            'subject_version_id' => (int)($session['subject_version_id'] ?? 0),
            'unit_id' => isset($session['unit_id']) && $session['unit_id'] !== null
                ? (int)$session['unit_id']
                : null,
            'lesson_id' => isset($session['lesson_id']) && $session['lesson_id'] !== null
                ? (int)$session['lesson_id']
                : null,
            'activity_type' => (string)($session['activity_type'] ?? ''),
            'activity_mode' => (string)($session['activity_mode'] ?? ''),
        ],
        'progress' => [
            'current_index' => $answered,
            'total_questions' => count($questions),
        ],
        'policy' => api_test_policy_public(api_test_policy_for_session($pdo, $session)),
        'questions' => $questions,
    ];
    $data['version'] = hash(
        'sha256',
        json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
    );
    return $data;
}
