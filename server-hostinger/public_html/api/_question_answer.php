<?php
declare(strict_types=1);

require_once __DIR__ . '/_question_session.php';

final class ApiQuestionAnswerRejected extends RuntimeException
{
    public function __construct(
        public readonly string $apiCode,
        public readonly int $status,
        string $message,
    ) {
        parent::__construct($message);
    }
}

function api_question_answer_reject(string $code, string $message, int $status): never
{
    throw new ApiQuestionAnswerRejected($code, $status, $message);
}

function api_question_answer_text(array $payload, string $key, int $maxLength = 128): string
{
    $value = trim((string)($payload[$key] ?? ''));
    if ($value === '' || strlen($value) > $maxLength) {
        api_error('invalid_answer', 'بيانات الإجابة غير صالحة.', 422);
    }
    return $value;
}

function api_question_answer_normalize(array $payload): array
{
    $sessionId = api_question_session_public_id((string)($payload['session_id'] ?? ''));
    $questionId = api_question_answer_text($payload, 'question_id', 64);
    if (!preg_match('/^[a-f0-9]{16,64}$/i', $questionId)) {
        api_error('invalid_answer', 'معرف السؤال غير صالح.', 422);
    }
    $answer = $payload['answer'] ?? null;
    if (!is_array($answer)) {
        api_error('invalid_answer', 'الإجابة غير صالحة.', 422);
    }

    $kind = trim((string)($answer['kind'] ?? ''));
    $normalized = match ($kind) {
        'choice' => [
            'kind' => 'choice',
            'option_id' => api_question_answer_text($answer, 'option_id', 64),
        ],
        'text' => [
            'kind' => 'text',
            'text' => api_question_answer_text($answer, 'text', 2000),
        ],
        'connections' => [
            'kind' => 'connections',
            'pairs' => api_question_answer_normalize_pairs($answer['pairs'] ?? null),
        ],
        default => api_error('invalid_answer', 'نوع الإجابة غير صالح.', 422),
    };

    return [
        'session_id' => $sessionId,
        'question_id' => strtolower($questionId),
        'answer' => $normalized,
    ];
}

function api_question_answer_normalize_pairs(mixed $value): array
{
    if (!is_array($value) || $value === []) {
        api_error('invalid_answer', 'أزواج التوصيل غير صالحة.', 422);
    }
    $pairs = [];
    foreach ($value as $pair) {
        if (!is_array($pair)) {
            api_error('invalid_answer', 'أزواج التوصيل غير صالحة.', 422);
        }
        $left = api_question_answer_text($pair, 'left_id', 64);
        $right = api_question_answer_text($pair, 'right_id', 64);
        $pairs[] = ['left_id' => strtolower($left), 'right_id' => strtolower($right)];
    }
    usort($pairs, static fn(array $a, array $b): int => strcmp($a['left_id'], $b['left_id']));
    if (
        count(array_unique(array_column($pairs, 'left_id'))) !== count($pairs)
        || count(array_unique(array_column($pairs, 'right_id'))) !== count($pairs)
    ) {
        api_error('invalid_answer', 'لا يمكن استخدام عنصر التوصيل أكثر من مرة.', 422);
    }
    return $pairs;
}

function api_question_answer_request_hash(array $request): string
{
    return hash(
        'sha256',
        json_encode($request, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
    );
}

function api_question_answer_source_rows(
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
    $scope = api_question_session_scope($session, $columns);
    if ($idColumn === null || $scope === null) {
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

    try {
        $statement = $pdo->prepare(
            'SELECT q.* FROM `' . $table . '` q'
            . $scope['joins']
            . ' WHERE ' . implode(' AND ', $where)
            . " ORDER BY q.`{$idColumn}` ASC LIMIT " . API_QUESTION_SESSION_MAX_QUESTIONS,
        );
        $statement->execute($params);
        return [
            'table' => $table,
            'columns' => $columns,
            'id_column' => $idColumn,
            'rows' => $statement->fetchAll(PDO::FETCH_ASSOC) ?: [],
        ];
    } catch (Throwable) {
        return null;
    }
}


function api_question_answer_resolve_normalized_source(
    PDO $pdo,
    array $session,
    string $questionType,
    string $questionId,
): ?array {
    $rows = api_question_session_normalized_rows($pdo, $session, $questionType);
    if ($rows === null) {
        return null;
    }
    foreach ($rows as $row) {
        $rowId = (string)($row['_id'] ?? '');
        if ($rowId === '') {
            continue;
        }
        $opaque = api_question_session_opaque_id(
            (string)$session['public_session_id'],
            'questions',
            $rowId,
        );
        if (hash_equals($opaque, $questionId)) {
            return [
                'table' => 'questions',
                'columns' => api_question_session_columns($pdo, 'questions'),
                'id_column' => 'id',
                'rows' => $rows,
                'question_type' => $questionType,
                'row' => $row,
                'row_id' => $rowId,
                'normalized_schema' => true,
            ];
        }
    }
    return null;
}

function api_question_answer_resolve_source(
    PDO $pdo,
    array $session,
    string $questionId,
): array {
    $activityType = (string)($session['activity_type'] ?? '');
    $questionType = api_question_session_question_type($activityType);
    $definition = api_question_session_tool_definition($activityType);
    if ($questionType === null || $definition === null || !empty($definition['review'])) {
        api_error('question_source_unavailable', 'مصدر السؤال غير متاح.', 503);
    }

    foreach ((array)($definition['tables'] ?? []) as $candidate) {
        if ((string)($candidate['table'] ?? '') === 'questions') {
            $normalized = api_question_answer_resolve_normalized_source(
                $pdo,
                $session,
                $questionType,
                $questionId,
            );
            if ($normalized !== null) {
                return $normalized;
            }
        }

        $source = api_question_answer_source_rows($pdo, $session, (array)$candidate);
        if ($source === null || $source['rows'] === []) {
            continue;
        }
        $table = (string)$source['table'];
        if ($questionType === 'connect') {
            $bundleId = api_question_session_opaque_id(
                (string)$session['public_session_id'],
                $table,
                'bundle',
                'connect',
            );
            if (hash_equals($bundleId, $questionId)) {
                return $source + ['question_type' => $questionType];
            }
            continue;
        }

        foreach ($source['rows'] as $row) {
            $rowId = (string)($row[$source['id_column']] ?? '');
            if ($rowId === '') {
                continue;
            }
            $opaque = api_question_session_opaque_id(
                (string)$session['public_session_id'],
                $table,
                $rowId,
            );
            if (hash_equals($opaque, $questionId)) {
                return $source + [
                    'question_type' => $questionType,
                    'row' => $row,
                    'row_id' => $rowId,
                ];
            }
        }
    }
    api_error('question_not_found', 'السؤال غير متاح داخل هذه الجلسة.', 404);
}

function api_question_answer_correct_column(array $columns): ?string
{
    return api_question_session_first_column(
        $columns,
        [
            'correct_answer',
            'correct_option',
            'correct_choice',
            'right_answer',
            'answer',
            'solution',
            'correct',
        ],
    );
}

function api_question_answer_normalized_value(string $value): string
{
    $value = trim($value);
    return function_exists('mb_strtolower') ? mb_strtolower($value, 'UTF-8') : strtolower($value);
}

function api_question_answer_option_columns(array $columns): array
{
    foreach ([
        ['option_a', 'option_b', 'option_c', 'option_d'],
        ['choice_a', 'choice_b', 'choice_c', 'choice_d'],
    ] as $group) {
        $available = array_values(array_filter($group, static fn(string $key): bool => isset($columns[$key])));
        if (count($available) >= 2) {
            return $available;
        }
    }
    return [];
}

function api_question_answer_expected_option_column(array $row, array $columns): ?string
{
    $correctColumn = api_question_answer_correct_column($columns);
    if ($correctColumn === null) {
        return null;
    }
    $raw = api_question_answer_normalized_value((string)($row[$correctColumn] ?? ''));
    if ($raw === '') {
        return null;
    }

    $optionColumns = api_question_answer_option_columns($columns);
    foreach ($optionColumns as $index => $optionColumn) {
        $optionText = api_question_answer_normalized_value((string)($row[$optionColumn] ?? ''));
        $aliases = [
            api_question_answer_normalized_value($optionColumn),
            api_question_answer_normalized_value(str_replace(['option_', 'choice_'], '', $optionColumn)),
            (string)($index + 1),
            $optionText,
        ];
        if (in_array($raw, $aliases, true)) {
            return $optionColumn;
        }
    }
    return null;
}

function api_question_answer_grade_choice(array $session, array $source, array $answer): bool
{
    if (($answer['kind'] ?? '') !== 'choice') {
        api_error('invalid_answer', 'هذه الجلسة تتطلب اختيار إجابة.', 422);
    }
    $row = (array)($source['row'] ?? []);
    $rowId = (string)($source['row_id'] ?? '');
    $expectedColumn = api_question_answer_expected_option_column($row, (array)$source['columns']);
    if ($expectedColumn === null) {
        api_error('grading_source_unavailable', 'تعذر إثبات مفتاح تصحيح هذا السؤال.', 503);
    }
    $expectedToken = api_question_session_opaque_id(
        (string)$session['public_session_id'],
        (string)$source['table'],
        $rowId,
        $expectedColumn,
    );
    return hash_equals($expectedToken, strtolower((string)$answer['option_id']));
}

function api_question_answer_grade_truefalse(array $session, array $source, array $answer): bool
{
    if (($answer['kind'] ?? '') !== 'choice') {
        api_error('invalid_answer', 'هذا السؤال يتطلب اختيار صح أو خطأ.', 422);
    }
    $row = (array)($source['row'] ?? []);
    $correctColumn = api_question_answer_correct_column((array)$source['columns']);
    if ($correctColumn === null) {
        api_error('grading_source_unavailable', 'تعذر إثبات مفتاح تصحيح هذا السؤال.', 503);
    }
    $raw = api_question_answer_normalized_value((string)($row[$correctColumn] ?? ''));
    $isTrue = in_array($raw, ['1', 'true', 'yes', 'صح', 'صحيح'], true);
    $isFalse = in_array($raw, ['0', 'false', 'no', 'خطأ', 'خاطئ'], true);
    if (!$isTrue && !$isFalse) {
        api_error('grading_source_unavailable', 'قيمة التصحيح لهذا السؤال غير معروفة.', 503);
    }
    $slot = $isTrue ? 'true' : 'false';
    $expectedToken = api_question_session_opaque_id(
        (string)$session['public_session_id'],
        (string)$source['table'],
        (string)$source['row_id'],
        $slot,
    );
    return hash_equals($expectedToken, strtolower((string)$answer['option_id']));
}

function api_question_answer_grade_fill(array $source, array $answer): bool
{
    if (($answer['kind'] ?? '') !== 'text') {
        api_error('invalid_answer', 'هذا السؤال يتطلب إجابة نصية.', 422);
    }
    $row = (array)($source['row'] ?? []);
    $correctColumn = api_question_answer_correct_column((array)$source['columns']);
    if ($correctColumn === null) {
        api_error('grading_source_unavailable', 'تعذر إثبات مفتاح تصحيح هذا السؤال.', 503);
    }
    $expected = api_question_answer_normalized_value((string)($row[$correctColumn] ?? ''));
    $actual = api_question_answer_normalized_value((string)($answer['text'] ?? ''));
    if ($expected === '') {
        api_error('grading_source_unavailable', 'قيمة التصحيح لهذا السؤال غير متاحة.', 503);
    }
    return hash_equals($expected, $actual);
}

function api_question_answer_grade_connect(array $session, array $source, array $answer): bool
{
    if (($answer['kind'] ?? '') !== 'connections') {
        api_error('invalid_answer', 'هذا السؤال يتطلب إكمال التوصيل.', 422);
    }
    $pairs = (array)($answer['pairs'] ?? []);
    $expected = [];
    foreach ((array)$source['rows'] as $row) {
        $rowId = (string)($row[$source['id_column']] ?? '');
        if ($rowId === '') {
            continue;
        }
        $left = api_question_session_opaque_id(
            (string)$session['public_session_id'],
            (string)$source['table'],
            $rowId,
            'left',
        );
        $right = api_question_session_opaque_id(
            (string)$session['public_session_id'],
            (string)$source['table'],
            $rowId,
            'right',
        );
        $expected[strtolower($left)] = strtolower($right);
    }
    if ($expected === [] || count($pairs) !== count($expected)) {
        return false;
    }
    foreach ($pairs as $pair) {
        $left = strtolower((string)($pair['left_id'] ?? ''));
        $right = strtolower((string)($pair['right_id'] ?? ''));
        if (!isset($expected[$left]) || !hash_equals($expected[$left], $right)) {
            return false;
        }
    }
    return true;
}


function api_question_answer_grade_normalized_choice(
    PDO $pdo,
    array $session,
    array $source,
    array $answer,
): bool {
    if (($answer['kind'] ?? '') !== 'choice') {
        api_error('invalid_answer', 'هذه الجلسة تتطلب اختيار إجابة.', 422);
    }
    $columns = api_question_session_columns($pdo, 'question_mcq_options');
    if (!isset($columns['id'], $columns['question_id'], $columns['is_correct'])) {
        api_error('grading_source_unavailable', 'تعذر إثبات مفتاح تصحيح هذا السؤال.', 503);
    }

    try {
        $statement = $pdo->prepare(
            'SELECT id,is_correct FROM question_mcq_options WHERE question_id=? ORDER BY id ASC',
        );
        $statement->execute([(string)$source['row_id']]);
        $rows = $statement->fetchAll(PDO::FETCH_ASSOC) ?: [];
    } catch (Throwable) {
        api_error('grading_source_unavailable', 'تعذر قراءة خيارات تصحيح هذا السؤال.', 503);
    }

    $hasCorrect = false;
    $actual = strtolower((string)($answer['option_id'] ?? ''));
    foreach ($rows as $row) {
        if ((int)($row['is_correct'] ?? 0) !== 1) {
            continue;
        }
        $hasCorrect = true;
        $expected = api_question_session_opaque_id(
            (string)$session['public_session_id'],
            'questions',
            (string)$source['row_id'],
            'mcq-option:' . (string)($row['id'] ?? ''),
        );
        if (hash_equals($expected, $actual)) {
            return true;
        }
    }
    if (!$hasCorrect) {
        api_error('grading_source_unavailable', 'لا يوجد خيار صحيح مثبت لهذا السؤال.', 503);
    }
    return false;
}

function api_question_answer_grade_normalized_truefalse(
    PDO $pdo,
    array $session,
    array $source,
    array $answer,
): bool {
    if (($answer['kind'] ?? '') !== 'choice') {
        api_error('invalid_answer', 'هذا السؤال يتطلب اختيار صح أو خطأ.', 422);
    }
    $columns = api_question_session_columns($pdo, 'question_tf');
    if (!isset($columns['question_id'], $columns['correct_value'])) {
        api_error('grading_source_unavailable', 'تعذر إثبات مفتاح تصحيح هذا السؤال.', 503);
    }
    try {
        $statement = $pdo->prepare(
            'SELECT correct_value FROM question_tf WHERE question_id=? LIMIT 1',
        );
        $statement->execute([(string)$source['row_id']]);
        $raw = $statement->fetchColumn();
    } catch (Throwable) {
        api_error('grading_source_unavailable', 'تعذر قراءة مفتاح تصحيح هذا السؤال.', 503);
    }
    if ($raw === false || !in_array((string)$raw, ['0', '1'], true)) {
        api_error('grading_source_unavailable', 'قيمة التصحيح لهذا السؤال غير معروفة.', 503);
    }
    $slot = (string)$raw === '1' ? 'true' : 'false';
    $expected = api_question_session_opaque_id(
        (string)$session['public_session_id'],
        'questions',
        (string)$source['row_id'],
        $slot,
    );
    return hash_equals($expected, strtolower((string)($answer['option_id'] ?? '')));
}

function api_question_answer_grade_normalized_fill(
    PDO $pdo,
    array $source,
    array $answer,
): bool {
    if (($answer['kind'] ?? '') !== 'text') {
        api_error('invalid_answer', 'هذا السؤال يتطلب إجابة نصية.', 422);
    }
    $fillColumns = api_question_session_columns($pdo, 'question_fill');
    $answerColumns = api_question_session_columns($pdo, 'question_fill_answers');
    if (
        !isset($fillColumns['question_id'], $fillColumns['blanks_count'])
        || !isset($answerColumns['question_id'], $answerColumns['blank_index'], $answerColumns['answer_text'])
    ) {
        api_error('grading_source_unavailable', 'تعذر إثبات مفتاح تصحيح هذا السؤال.', 503);
    }
    try {
        $fill = $pdo->prepare('SELECT blanks_count FROM question_fill WHERE question_id=? LIMIT 1');
        $fill->execute([(string)$source['row_id']]);
        $blanksCount = (int)$fill->fetchColumn();
        $statement = $pdo->prepare(
            'SELECT answer_text FROM question_fill_answers '
            . 'WHERE question_id=? AND blank_index=1 ORDER BY id ASC',
        );
        $statement->execute([(string)$source['row_id']]);
        $accepted = $statement->fetchAll(PDO::FETCH_COLUMN) ?: [];
    } catch (Throwable) {
        api_error('grading_source_unavailable', 'تعذر قراءة إجابات التصحيح لهذا السؤال.', 503);
    }
    if ($blanksCount !== 1) {
        api_error('grading_source_unavailable', 'صيغة هذا السؤال تحتاج دعم أكثر من فراغ.', 503);
    }

    $actual = api_question_answer_normalized_value((string)($answer['text'] ?? ''));
    $hasAccepted = false;
    foreach ($accepted as $candidate) {
        $expected = api_question_answer_normalized_value((string)$candidate);
        if ($expected === '') {
            continue;
        }
        $hasAccepted = true;
        if (hash_equals($expected, $actual)) {
            return true;
        }
    }
    if (!$hasAccepted) {
        api_error('grading_source_unavailable', 'لا توجد إجابة تصحيح مثبتة لهذا السؤال.', 503);
    }
    return false;
}

function api_question_answer_grade_normalized_connect(
    PDO $pdo,
    array $session,
    array $source,
    array $answer,
): bool {
    if (($answer['kind'] ?? '') !== 'connections') {
        api_error('invalid_answer', 'هذا السؤال يتطلب إكمال التوصيل.', 422);
    }
    $columns = api_question_session_columns($pdo, 'question_match_pairs');
    if (!isset($columns['id'], $columns['question_id'])) {
        api_error('grading_source_unavailable', 'تعذر إثبات أزواج تصحيح هذا السؤال.', 503);
    }
    try {
        $statement = $pdo->prepare(
            'SELECT id FROM question_match_pairs WHERE question_id=? ORDER BY id ASC',
        );
        $statement->execute([(string)$source['row_id']]);
        $rows = $statement->fetchAll(PDO::FETCH_ASSOC) ?: [];
    } catch (Throwable) {
        api_error('grading_source_unavailable', 'تعذر قراءة أزواج تصحيح هذا السؤال.', 503);
    }

    $expected = [];
    foreach ($rows as $row) {
        $pairId = (string)($row['id'] ?? '');
        if ($pairId === '') {
            continue;
        }
        $left = api_question_session_opaque_id(
            (string)$session['public_session_id'],
            'questions',
            (string)$source['row_id'],
            'match-left:' . $pairId,
        );
        $right = api_question_session_opaque_id(
            (string)$session['public_session_id'],
            'questions',
            (string)$source['row_id'],
            'match-right:' . $pairId,
        );
        $expected[strtolower($left)] = strtolower($right);
    }
    $pairs = (array)($answer['pairs'] ?? []);
    if ($expected === [] || count($pairs) !== count($expected)) {
        return false;
    }
    foreach ($pairs as $pair) {
        $left = strtolower((string)($pair['left_id'] ?? ''));
        $right = strtolower((string)($pair['right_id'] ?? ''));
        if (!isset($expected[$left]) || !hash_equals($expected[$left], $right)) {
            return false;
        }
    }
    return true;
}

function api_question_answer_grade_normalized(
    PDO $pdo,
    array $session,
    array $source,
    array $answer,
): bool {
    return match ((string)$source['question_type']) {
        'choose', 'speed' => api_question_answer_grade_normalized_choice($pdo, $session, $source, $answer),
        'truefalse' => api_question_answer_grade_normalized_truefalse($pdo, $session, $source, $answer),
        'fill' => api_question_answer_grade_normalized_fill($pdo, $source, $answer),
        'connect' => api_question_answer_grade_normalized_connect($pdo, $session, $source, $answer),
        default => api_error('grading_source_unavailable', 'نوع التصحيح غير مدعوم.', 503),
    };
}

function api_question_answer_grade(
    array $session,
    array $source,
    array $answer,
    ?PDO $pdo = null,
): bool {
    if (!empty($source['normalized_schema'])) {
        if ($pdo === null) {
            api_error('grading_source_unavailable', 'اتصال قاعدة البيانات مطلوب للتصحيح.', 503);
        }
        return api_question_answer_grade_normalized($pdo, $session, $source, $answer);
    }
    return match ((string)$source['question_type']) {
        'choose', 'speed' => api_question_answer_grade_choice($session, $source, $answer),
        'truefalse' => api_question_answer_grade_truefalse($session, $source, $answer),
        'fill' => api_question_answer_grade_fill($source, $answer),
        'connect' => api_question_answer_grade_connect($session, $source, $answer),
        default => api_error('grading_source_unavailable', 'نوع التصحيح غير مدعوم.', 503),
    };
}

function api_question_answer_read_idempotency(
    PDO $pdo,
    int $studentId,
    string $keyHash,
): ?array {
    $statement = $pdo->prepare(
        'SELECT * FROM api_activity_answers WHERE user_id=? AND idempotency_key_hash=? LIMIT 1',
    );
    $statement->execute([$studentId, $keyHash]);
    return $statement->fetch(PDO::FETCH_ASSOC) ?: null;
}

function api_question_answer_read_question(
    PDO $pdo,
    int $studentId,
    string $sessionId,
    string $questionId,
): ?array {
    $statement = $pdo->prepare(
        'SELECT * FROM api_activity_answers '
        . 'WHERE user_id=? AND public_session_id=? AND question_id=? LIMIT 1',
    );
    $statement->execute([$studentId, $sessionId, $questionId]);
    return $statement->fetch(PDO::FETCH_ASSOC) ?: null;
}

function api_question_answer_stored_result(array $row, bool $replayed): array
{
    $result = json_decode((string)($row['result_json'] ?? ''), true);
    if (!is_array($result)) {
        api_question_answer_reject('answer_state_invalid', 'تعذر قراءة نتيجة الإجابة المخزنة.', 500);
    }
    $result['replayed'] = $replayed;
    return $result;
}

function api_question_answer_submit(
    PDO $pdo,
    array $authSession,
    array $payload,
    string $rawKey,
): array {
    $studentId = (int)($authSession['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }
    if (!api_activity_table_exists($pdo, 'api_activity_answers')) {
        api_error('answer_schema_missing', 'خدمة حفظ الإجابات لم تُجهّز في هذه البيئة بعد.', 503);
    }

    $request = api_question_answer_normalize($payload);
    $session = api_question_session_owned_row($pdo, $studentId, $request['session_id']);
    $source = api_question_answer_resolve_source($pdo, $session, $request['question_id']);
    $requestHash = api_question_answer_request_hash($request);
    $keyHash = api_activity_idempotency_hash(api_activity_idempotency_key($rawKey));
    $correct = api_question_answer_grade($session, $source, $request['answer'], $pdo);
    $totalQuestions = count(api_question_session_questions($pdo, $session));

    $ownsTransaction = !$pdo->inTransaction();
    if ($ownsTransaction) {
        $pdo->beginTransaction();
    }
    try {
        $existingKey = api_question_answer_read_idempotency($pdo, $studentId, $keyHash);
        if ($existingKey) {
            if (!hash_equals((string)$existingKey['request_hash'], $requestHash)) {
                api_question_answer_reject('idempotency_key_conflict', 'استُخدم مفتاح الإجابة لطلب مختلف.', 409);
            }
            $result = api_question_answer_stored_result($existingKey, true);
            if ($ownsTransaction) {
                $pdo->commit();
            }
            return $result;
        }

        $existingQuestion = api_question_answer_read_question(
            $pdo,
            $studentId,
            $request['session_id'],
            $request['question_id'],
        );
        if ($existingQuestion) {
            if (!hash_equals((string)$existingQuestion['request_hash'], $requestHash)) {
                api_question_answer_reject('question_already_answered', 'تم تثبيت إجابة مختلفة لهذا السؤال مسبقًا.', 409);
            }
            $result = api_question_answer_stored_result($existingQuestion, true);
            if ($ownsTransaction) {
                $pdo->commit();
            }
            return $result;
        }

        $countStatement = $pdo->prepare(
            'SELECT COUNT(*) FROM api_activity_answers WHERE user_id=? AND public_session_id=?',
        );
        $countStatement->execute([$studentId, $request['session_id']]);
        $answered = max(0, (int)$countStatement->fetchColumn()) + 1;

        $result = [
            'session_id' => $request['session_id'],
            'question_id' => $request['question_id'],
            'accepted' => true,
            'correct' => $correct,
            'replayed' => false,
            'progress' => [
                'answered' => min($answered, $totalQuestions),
                'total_questions' => $totalQuestions,
                'all_answered' => $answered >= $totalQuestions,
            ],
        ];
        $now = gmdate('Y-m-d H:i:s');
        $insert = $pdo->prepare(
            'INSERT INTO api_activity_answers '
            . '(user_id,public_session_id,question_id,idempotency_key_hash,request_hash,'
            . 'answer_json,result_json,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)',
        );
        $insert->execute([
            $studentId,
            $request['session_id'],
            $request['question_id'],
            $keyHash,
            $requestHash,
            json_encode($request['answer'], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
            json_encode($result, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
            $now,
            $now,
        ]);
        $update = $pdo->prepare(
            "UPDATE api_activity_sessions SET status='in_progress',updated_at=? "
            . "WHERE user_id=? AND public_session_id=? AND status IN ('created','in_progress')",
        );
        $update->execute([$now, $studentId, $request['session_id']]);

        if ($ownsTransaction) {
            $pdo->commit();
        }
        return $result;
    } catch (ApiQuestionAnswerRejected $error) {
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
