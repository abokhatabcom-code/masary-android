<?php
declare(strict_types=1);

function api_question_review_answer_map(
    PDO $pdo,
    int $studentId,
    string $sessionId,
): array {
    $statement = $pdo->prepare(
        'SELECT question_id,answer_json,result_json FROM api_activity_answers '
        . 'WHERE user_id=? AND public_session_id=? ORDER BY id ASC',
    );
    $statement->execute([$studentId, $sessionId]);
    $map = [];
    foreach ($statement->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
        $map[(string)$row['question_id']] = [
            'answer' => json_decode((string)($row['answer_json'] ?? ''), true),
            'result' => json_decode((string)($row['result_json'] ?? ''), true),
        ];
    }
    return $map;
}

function api_question_review_option_text(array $payload, string $id, string $key = 'options'): string
{
    foreach ((array)($payload[$key] ?? []) as $option) {
        if ((string)($option['id'] ?? '') === $id) {
            return trim((string)($option['text'] ?? ''));
        }
    }
    return '';
}

function api_question_review_connect_text(array $payload, array $pairs): string
{
    $left = [];
    foreach ((array)($payload['left_items'] ?? []) as $item) {
        $left[(string)($item['id'] ?? '')] = trim((string)($item['text'] ?? ''));
    }
    $right = [];
    foreach ((array)($payload['right_items'] ?? []) as $item) {
        $right[(string)($item['id'] ?? '')] = trim((string)($item['text'] ?? ''));
    }

    $lines = [];
    foreach ($pairs as $pair) {
        $leftText = $left[(string)($pair['left_id'] ?? '')] ?? '';
        $rightText = $right[(string)($pair['right_id'] ?? '')] ?? '';
        if ($leftText !== '' || $rightText !== '') {
            $lines[] = trim($leftText . ' ← ' . $rightText);
        }
    }
    return implode(' • ', $lines);
}

function api_question_review_student_answer(array $question, ?array $answer): string
{
    if (!is_array($answer)) {
        return 'لم تُجب';
    }
    $kind = (string)($answer['kind'] ?? '');
    $payload = (array)($question['payload'] ?? []);

    return match ($kind) {
        'choice' => trim(implode(' • ', array_filter([
            api_question_review_option_text(
                $payload,
                (string)($answer['option_id'] ?? ''),
                'options',
            ),
            api_question_review_option_text(
                $payload,
                (string)($answer['reason_id'] ?? ''),
                'reasons',
            ),
        ], static fn(string $value): bool => $value !== ''))),
        'text' => trim((string)($answer['text'] ?? '')),
        'fill' => implode(' • ', array_map(
            static fn(mixed $value): string => trim((string)$value),
            (array)($answer['blanks'] ?? []),
        )),
        'connections' => api_question_review_connect_text(
            $payload,
            (array)($answer['pairs'] ?? []),
        ),
        'skip' => 'تم التخطي',
        default => '',
    };
}

function api_question_review_normalized_correct(
    PDO $pdo,
    array $session,
    array $source,
): string {
    $questionType = (string)($source['question_type'] ?? '');
    $rowId = (string)($source['row_id'] ?? '');
    if (empty($source['normalized_schema']) || $rowId === '') {
        return '';
    }

    try {
        if (in_array($questionType, ['choose', 'speed'], true)) {
            $columns = api_question_session_columns($pdo, 'question_mcq_options');
            if (!isset($columns['question_id'], $columns['option_text'], $columns['is_correct'])) {
                return '';
            }
            $statement = $pdo->prepare(
                'SELECT option_text FROM question_mcq_options '
                . 'WHERE question_id=? AND is_correct=1 ORDER BY id ASC',
            );
            $statement->execute([$rowId]);
            return implode(' • ', array_values(array_filter(array_map(
                'trim',
                $statement->fetchAll(PDO::FETCH_COLUMN) ?: [],
            ))));
        }

        if ($questionType === 'truefalse') {
            $columns = api_question_session_columns($pdo, 'question_tf');
            if (!isset($columns['question_id'], $columns['correct_value'])) {
                return '';
            }
            $select = isset($columns['requires_reason'])
                ? 'correct_value,requires_reason'
                : 'correct_value,0 AS requires_reason';
            $statement = $pdo->prepare(
                'SELECT ' . $select . ' FROM question_tf WHERE question_id=? LIMIT 1',
            );
            $statement->execute([$rowId]);
            $row = $statement->fetch(PDO::FETCH_ASSOC);
            if (!$row) return '';
            $parts = [(int)($row['correct_value'] ?? 0) === 1 ? 'صح' : 'خطأ'];
            if ((int)($row['requires_reason'] ?? 0) === 1) {
                $reasonColumns = api_question_session_columns($pdo, 'question_tf_reasons');
                if (isset($reasonColumns['question_id'], $reasonColumns['label'], $reasonColumns['is_correct'])) {
                    $reason = $pdo->prepare(
                        'SELECT label FROM question_tf_reasons '
                        . 'WHERE question_id=? AND is_correct=1 ORDER BY id ASC LIMIT 1',
                    );
                    $reason->execute([$rowId]);
                    $label = trim((string)($reason->fetchColumn() ?: ''));
                    if ($label !== '') $parts[] = $label;
                }
            }
            return implode(' • ', $parts);
        }

        if ($questionType === 'fill') {
            $fillColumns = api_question_session_columns($pdo, 'question_fill');
            $answerColumns = api_question_session_columns($pdo, 'question_fill_answers');
            if (!isset($fillColumns['question_id'], $fillColumns['blanks_count'])
                || !isset($answerColumns['question_id'], $answerColumns['blank_index'], $answerColumns['answer_text'])) {
                return '';
            }
            $count = $pdo->prepare(
                'SELECT blanks_count FROM question_fill WHERE question_id=? LIMIT 1',
            );
            $count->execute([$rowId]);
            $blanks = max(1, min(4, (int)$count->fetchColumn()));
            $parts = [];
            for ($index = 1; $index <= $blanks; $index++) {
                $answer = $pdo->prepare(
                    'SELECT answer_text FROM question_fill_answers '
                    . 'WHERE question_id=? AND blank_index=? ORDER BY id ASC LIMIT 1',
                );
                $answer->execute([$rowId, $index]);
                $value = trim((string)($answer->fetchColumn() ?: ''));
                if ($value !== '') $parts[] = $value;
            }
            return implode(' • ', $parts);
        }

        if ($questionType === 'direct') {
            $columns = api_question_session_columns($pdo, 'question_direct');
            if (!isset($columns['question_id'], $columns['answer_text'])) return '';
            $statement = $pdo->prepare(
                'SELECT answer_text FROM question_direct WHERE question_id=? LIMIT 1',
            );
            $statement->execute([$rowId]);
            return trim((string)($statement->fetchColumn() ?: ''));
        }

        if ($questionType === 'connect') {
            $columns = api_question_session_columns($pdo, 'question_match_pairs');
            if (!isset($columns['question_id'], $columns['left_text'], $columns['right_text'])) {
                return '';
            }
            $statement = $pdo->prepare(
                'SELECT left_text,right_text FROM question_match_pairs '
                . 'WHERE question_id=? ORDER BY id ASC',
            );
            $statement->execute([$rowId]);
            $parts = [];
            foreach ($statement->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
                $left = trim((string)($row['left_text'] ?? ''));
                $right = trim((string)($row['right_text'] ?? ''));
                if ($left !== '' || $right !== '') {
                    $parts[] = trim($left . ' ← ' . $right);
                }
            }
            return implode(' • ', $parts);
        }
    } catch (Throwable) {
        return '';
    }
    return '';
}

function api_question_review_items(
    PDO $pdo,
    int $studentId,
    array $session,
    array $questions,
    array $policy,
): array {
    if (empty($policy['result_show_review_details'])) {
        return [];
    }

    $answers = api_question_review_answer_map(
        $pdo,
        $studentId,
        (string)($session['public_session_id'] ?? ''),
    );
    $reveal = !empty($policy['reveal_answers']);
    $items = [];

    foreach ($questions as $index => $question) {
        $questionId = (string)($question['id'] ?? '');
        if ($questionId === '') continue;
        $answerState = $answers[$questionId] ?? null;
        $result = is_array($answerState) ? ($answerState['result'] ?? null) : null;
        $answer = is_array($answerState) ? ($answerState['answer'] ?? null) : null;
        $score = is_array($result)
            ? max(0.0, min(1.0, (float)($result['score'] ?? (!empty($result['correct']) ? 1 : 0))))
            : 0.0;
        $status = $score >= 0.999
            ? 'correct'
            : ($score > 0.000001 ? 'partial' : 'wrong');

        $correctAnswer = null;
        if ($reveal) {
            try {
                $source = api_question_answer_resolve_source($pdo, $session, $questionId);
                $correct = api_question_review_normalized_correct($pdo, $session, $source);
                $correctAnswer = $correct !== '' ? $correct : null;
            } catch (Throwable) {
                $correctAnswer = null;
            }
        }

        $items[] = [
            'index' => $index + 1,
            'question_id' => $questionId,
            'type' => (string)($question['type'] ?? ''),
            'prompt' => trim((string)($question['prompt'] ?? '')),
            'score' => round($score, 6),
            'status' => $status,
            'student_answer' => api_question_review_student_answer(
                (array)$question,
                is_array($answer) ? $answer : null,
            ),
            'correct_answer' => $correctAnswer,
        ];
    }
    return $items;
}
