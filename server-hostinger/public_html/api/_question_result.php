<?php
declare(strict_types=1);

require_once __DIR__ . '/_question_answer.php';
require_once __DIR__ . '/_question_progress.php';

final class ApiQuestionResultRejected extends RuntimeException
{
    public function __construct(
        public readonly string $apiCode,
        public readonly int $status,
        string $message,
    ) {
        parent::__construct($message);
    }
}

function api_question_result_reject(string $code, string $message, int $status): never
{
    throw new ApiQuestionResultRejected($code, $status, $message);
}

function api_question_result_request(array $payload): array
{
    return [
        'session_id' => api_question_session_public_id((string)($payload['session_id'] ?? '')),
    ];
}

function api_question_result_request_hash(array $request): string
{
    return hash(
        'sha256',
        json_encode($request, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
    );
}

function api_question_result_read_key(PDO $pdo, int $studentId, string $keyHash): ?array
{
    $statement = $pdo->prepare(
        'SELECT * FROM api_activity_results WHERE user_id=? AND idempotency_key_hash=? LIMIT 1',
    );
    $statement->execute([$studentId, $keyHash]);
    return $statement->fetch(PDO::FETCH_ASSOC) ?: null;
}

function api_question_result_read_session(PDO $pdo, int $studentId, string $sessionId): ?array
{
    $statement = $pdo->prepare(
        'SELECT * FROM api_activity_results WHERE user_id=? AND public_session_id=? LIMIT 1',
    );
    $statement->execute([$studentId, $sessionId]);
    return $statement->fetch(PDO::FETCH_ASSOC) ?: null;
}

function api_question_result_stored(array $row, bool $replayed): array
{
    $result = json_decode((string)($row['result_json'] ?? ''), true);
    if (!is_array($result)) {
        api_question_result_reject('result_state_invalid', 'تعذر قراءة نتيجة الجلسة المخزنة.', 500);
    }
    $result['replayed'] = $replayed;
    return $result;
}

function api_question_result_score(PDO $pdo, int $studentId, string $sessionId): array
{
    $statement = $pdo->prepare(
        'SELECT result_json FROM api_activity_answers '
        . 'WHERE user_id=? AND public_session_id=? ORDER BY id ASC',
    );
    $statement->execute([$studentId, $sessionId]);
    $rows = $statement->fetchAll(PDO::FETCH_ASSOC) ?: [];

    $correct = 0;
    $partial = 0;
    $wrong = 0;
    $points = 0.0;
    foreach ($rows as $row) {
        $result = json_decode((string)($row['result_json'] ?? ''), true);
        if (!is_array($result) || !array_key_exists('correct', $result)) {
            api_question_result_reject(
                'answer_state_invalid',
                'تعذر إثبات نتيجة إحدى إجابات الجلسة.',
                500,
            );
        }

        $score = array_key_exists('score', $result)
            ? (float)$result['score']
            : (!empty($result['correct']) ? 1.0 : 0.0);
        $score = max(0.0, min(1.0, $score));
        $points += $score;

        if ($score >= 0.999) {
            $correct += 1;
        } elseif ($score > 0.000001) {
            $partial += 1;
        } else {
            $wrong += 1;
        }
    }

    $total = count($rows);
    $percentExact = $total > 0 ? ($points / $total) * 100.0 : 0.0;
    return [
        'correct_answers' => $correct,
        // Keep incorrect_answers as non-fully-correct for backward compatibility.
        'incorrect_answers' => max(0, $total - $correct),
        'partial_answers' => $partial,
        'wrong_answers' => $wrong,
        'total_questions' => $total,
        'score_points' => round($points, 6),
        'score_percent_exact' => round($percentExact, 6),
        'score_percent' => (int)round($percentExact),
    ];
}

function api_question_result_finish(
    PDO $pdo,
    array $authSession,
    array $payload,
    string $rawKey,
): array {
    $studentId = (int)($authSession['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }
    if (!api_activity_table_exists($pdo, 'api_activity_answers')
        || !api_activity_table_exists($pdo, 'api_activity_results')) {
        api_error('result_schema_missing', 'خدمة إنهاء جلسة الأسئلة لم تُجهّز في هذه البيئة بعد.', 503);
    }

    $request = api_question_result_request($payload);
    $requestHash = api_question_result_request_hash($request);
    $keyHash = api_activity_idempotency_hash(api_activity_idempotency_key($rawKey));

    $ownsTransaction = !$pdo->inTransaction();
    if ($ownsTransaction) {
        $pdo->beginTransaction();
    }

    try {
        $existingKey = api_question_result_read_key($pdo, $studentId, $keyHash);
        if ($existingKey) {
            if (!hash_equals((string)$existingKey['request_hash'], $requestHash)) {
                api_question_result_reject(
                    'idempotency_key_conflict',
                    'استُخدم مفتاح إنهاء الجلسة لطلب مختلف.',
                    409,
                );
            }
            $result = api_question_result_stored($existingKey, true);
            if ($ownsTransaction) {
                $pdo->commit();
            }
            return $result;
        }

        $existingSession = api_question_result_read_session(
            $pdo,
            $studentId,
            $request['session_id'],
        );
        if ($existingSession) {
            $result = api_question_result_stored($existingSession, true);
            if ($ownsTransaction) {
                $pdo->commit();
            }
            return $result;
        }

        // Only a new completion needs an active owned activity session.
        // Stored final results remain replayable after the activity expiry time.
        $session = api_question_session_owned_row($pdo, $studentId, $request['session_id']);
        $questions = api_question_session_questions($pdo, $session);
        $totalQuestions = count($questions);
        if ($totalQuestions <= 0) {
            api_question_result_reject(
                'question_source_unavailable',
                'لا توجد أسئلة مؤكدة لهذه الجلسة.',
                503,
            );
        }

        $count = $pdo->prepare(
            'SELECT COUNT(*) FROM api_activity_answers WHERE user_id=? AND public_session_id=?',
        );
        $count->execute([$studentId, $request['session_id']]);
        $answered = max(0, (int)$count->fetchColumn());
        if ($answered < $totalQuestions) {
            api_question_result_reject(
                'session_incomplete',
                'لا يمكن إنهاء الجلسة قبل تثبيت جميع الإجابات.',
                409,
            );
        }

        $score = api_question_result_score($pdo, $studentId, $request['session_id']);
        if ((int)$score['total_questions'] !== $totalQuestions) {
            api_question_result_reject(
                'session_answer_count_mismatch',
                'عدد الإجابات المؤكدة لا يطابق حزمة الجلسة.',
                409,
            );
        }

        $policy = api_test_policy_for_session($pdo, $session);
        $progress = api_question_progress_apply(
            $pdo,
            $studentId,
            $session,
            $score,
            $policy,
        );
        $passPercent = max(1, min(100, (int)($policy['pass_percent'] ?? 60)));
        $passed = (float)($score['score_percent'] ?? 0) >= $passPercent;

        $now = gmdate('Y-m-d H:i:s');
        $result = [
            'session_id' => $request['session_id'],
            'status' => 'completed',
            'completed_at' => $now,
            'replayed' => false,
            'result' => array_merge($score, [
                'passed' => $passed,
                'pass_percent' => $passPercent,
                'xp_earned' => (float)($progress['xp_earned'] ?? 0),
                'hearts_spent' => max(0, (int)($session['heart_debited'] ?? 0)),
            ]),
            'policy' => api_test_policy_public($policy),
            'confirmed_delta' => (array)$progress['confirmed_delta'],
        ];

        $insert = $pdo->prepare(
            'INSERT INTO api_activity_results '
            . '(user_id,public_session_id,idempotency_key_hash,request_hash,result_json,created_at,updated_at) '
            . 'VALUES (?,?,?,?,?,?,?)',
        );
        $insert->execute([
            $studentId,
            $request['session_id'],
            $keyHash,
            $requestHash,
            json_encode($result, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
            $now,
            $now,
        ]);

        $update = $pdo->prepare(
            "UPDATE api_activity_sessions SET status='completed',completed_at=?,updated_at=? "
            . "WHERE user_id=? AND public_session_id=? AND status IN ('created','in_progress','completed')",
        );
        $update->execute([$now, $now, $studentId, $request['session_id']]);

        if ($ownsTransaction) {
            $pdo->commit();
        }
        return $result;
    } catch (ApiQuestionResultRejected $error) {
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
