<?php
declare(strict_types=1);

require_once __DIR__ . '/_student_training_center.php';
require_once __DIR__ . '/_activity_preparation.php';
require_once __DIR__ . '/_activity_preparation_lifecycle.php';

const API_QUESTION_SESSION_MAX_QUESTIONS = 50;

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

function api_question_session_questions(PDO $pdo, array $session): array
{
    $activityType = (string)($session['activity_type'] ?? '');
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

function api_question_session_package(PDO $pdo, array $authSession, string $sessionId): array
{
    $studentId = (int)($authSession['user_id'] ?? 0);
    $session = api_question_session_owned_row($pdo, $studentId, $sessionId);
    $questions = api_question_session_questions($pdo, $session);

    $data = [
        'generated_at' => gmdate(DATE_ATOM),
        'session' => [
            'id' => (string)$session['public_session_id'],
            'status' => (string)($session['status'] ?? 'created'),
            'expires_at' => (string)($session['expires_at'] ?? ''),
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
            'current_index' => 0,
            'total_questions' => count($questions),
        ],
        'questions' => $questions,
    ];
    $data['version'] = hash(
        'sha256',
        json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
    );
    return $data;
}
