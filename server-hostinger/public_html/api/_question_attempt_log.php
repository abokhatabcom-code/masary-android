<?php
declare(strict_types=1);

/**
 * Bridges Phase 13.6 completions into the platform's established attempt/reporting tables.
 * All writes happen inside the same transaction as result + XP confirmation.
 */

function api_question_attempt_driver(PDO $pdo): string
{
    return strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME));
}

function api_question_attempt_day_key(): string
{
    try {
        return (new DateTime('now', new DateTimeZone('Asia/Aden')))->format('Y-m-d');
    } catch (Throwable) {
        return gmdate('Y-m-d');
    }
}

function api_question_attempt_now_local(): string
{
    try {
        return (new DateTime('now', new DateTimeZone('Asia/Aden')))->format('Y-m-d H:i:s');
    } catch (Throwable) {
        return gmdate('Y-m-d H:i:s');
    }
}

function api_question_attempt_duration(
    array $session,
    array $policy,
    ?int $clientActiveSeconds,
): int {
    $started = strtotime((string)($session['started_at'] ?? ''));
    $raw = $started !== false ? max(0, time() - $started) : 0;

    if (empty($policy['active_time_enabled'])) {
        return $raw;
    }
    if ($clientActiveSeconds === null) {
        // Legacy fallback: if a client cannot provide active time, keep elapsed time
        // rather than recording a misleading zero.
        return $raw;
    }
    return max(0, min($raw, $clientActiveSeconds));
}

function api_question_attempt_source_map(
    PDO $pdo,
    array $session,
    array $questions,
): array {
    $map = [];
    foreach ($questions as $question) {
        $opaqueId = trim((string)($question['id'] ?? ''));
        if ($opaqueId === '') continue;
        $source = api_question_answer_resolve_source($pdo, $session, $opaqueId);
        $sourceId = (int)($source['row_id'] ?? 0);
        if ($sourceId <= 0) continue;
        $map[$opaqueId] = [
            'id' => $sourceId,
            'type' => (string)($source['question_type'] ?? ($question['type'] ?? '')),
        ];
    }
    return $map;
}

function api_question_attempt_current_answers(
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

function api_question_attempt_upsert_question_state(
    PDO $pdo,
    int $studentId,
    int $unitId,
    int $questionId,
    float $score,
    int $attemptId,
    string $now,
): void {
    if ($unitId <= 0 || !api_activity_table_exists($pdo, 'student_unit_question_state')) {
        return;
    }

    if (api_question_attempt_driver($pdo) === 'sqlite') {
        $statement = $pdo->prepare(
            'INSERT INTO student_unit_question_state'
            . '(student_id,unit_id,question_id,last_score,last_attempt_id,updated_at) '
            . 'VALUES(?,?,?,?,?,?) '
            . 'ON CONFLICT(student_id,unit_id,question_id) DO UPDATE SET '
            . 'last_score=excluded.last_score,last_attempt_id=excluded.last_attempt_id,'
            . 'updated_at=excluded.updated_at',
        );
    } else {
        $statement = $pdo->prepare(
            'INSERT INTO student_unit_question_state'
            . '(student_id,unit_id,question_id,last_score,last_attempt_id,updated_at) '
            . 'VALUES(?,?,?,?,?,?) '
            . 'ON DUPLICATE KEY UPDATE last_score=VALUES(last_score),'
            . 'last_attempt_id=VALUES(last_attempt_id),updated_at=VALUES(updated_at)',
        );
    }
    $statement->execute([$studentId, $unitId, $questionId, $score, $attemptId, $now]);
}

function api_question_attempt_update_daily_time(
    PDO $pdo,
    int $studentId,
    int $durationSeconds,
    int $questionCount,
): array {
    if (!api_activity_table_exists($pdo, 'learning_time_daily')) {
        return [];
    }

    $day = api_question_attempt_day_key();
    if (api_question_attempt_driver($pdo) === 'sqlite') {
        $statement = $pdo->prepare(
            'INSERT INTO learning_time_daily'
            . '(student_id,day,seconds_total,sessions_count,questions_total) '
            . 'VALUES(?,?,?,?,?) '
            . 'ON CONFLICT(student_id,day) DO UPDATE SET '
            . 'seconds_total=seconds_total+excluded.seconds_total,'
            . 'sessions_count=sessions_count+excluded.sessions_count,'
            . 'questions_total=questions_total+excluded.questions_total',
        );
    } else {
        $statement = $pdo->prepare(
            'INSERT INTO learning_time_daily'
            . '(student_id,day,seconds_total,sessions_count,questions_total) '
            . 'VALUES(?,?,?,?,?) '
            . 'ON DUPLICATE KEY UPDATE '
            . 'seconds_total=seconds_total+VALUES(seconds_total),'
            . 'sessions_count=sessions_count+VALUES(sessions_count),'
            . 'questions_total=questions_total+VALUES(questions_total)',
        );
    }
    $statement->execute([
        $studentId,
        $day,
        max(0, $durationSeconds),
        1,
        max(0, $questionCount),
    ]);

    $read = $pdo->prepare(
        'SELECT seconds_total,sessions_count,questions_total '
        . 'FROM learning_time_daily WHERE student_id=? AND day=? LIMIT 1',
    );
    $read->execute([$studentId, $day]);
    return $read->fetch(PDO::FETCH_ASSOC) ?: [];
}

function api_question_attempt_log(
    PDO $pdo,
    int $studentId,
    array $session,
    array $questions,
    array $score,
    array $policy,
    float $xpEarned,
    string $mode,
    ?int $clientActiveSeconds,
): array {
    if (!api_activity_table_exists($pdo, 'test_attempts')) {
        return [
            'attempt_id' => null,
            'duration_seconds' => api_question_attempt_duration(
                $session,
                $policy,
                $clientActiveSeconds,
            ),
            'today' => [],
        ];
    }

    $sessionId = (string)($session['public_session_id'] ?? '');
    $subjectVersionId = (int)($session['subject_version_id'] ?? 0);
    $unitId = (int)($session['unit_id'] ?? 0);
    $heartsSpent = max(0, (int)($session['heart_debited'] ?? 0));
    $duration = api_question_attempt_duration($session, $policy, $clientActiveSeconds);
    $sourceMap = api_question_attempt_source_map($pdo, $session, $questions);
    $currentAnswers = api_question_attempt_current_answers($pdo, $studentId, $sessionId);

    $questionList = [];
    $answersBySource = [];
    $scoring = [];
    foreach ($questions as $question) {
        $opaqueId = (string)($question['id'] ?? '');
        $source = $sourceMap[$opaqueId] ?? null;
        if (!$source) continue;

        $sourceId = (int)$source['id'];
        $type = (string)$source['type'];
        $answerState = $currentAnswers[$opaqueId] ?? null;
        $answer = is_array($answerState) ? ($answerState['answer'] ?? null) : null;
        $answerResult = is_array($answerState) ? ($answerState['result'] ?? null) : null;
        $questionScore = is_array($answerResult)
            ? max(0.0, min(1.0, (float)($answerResult['score'] ?? (!empty($answerResult['correct']) ? 1 : 0))))
            : 0.0;

        $questionList[] = ['id' => $sourceId, 'type' => $type];
        $answersBySource[(string)$sourceId] = $answer;
        $scoring[] = [
            'question_id' => $sourceId,
            'type' => $type,
            'score' => $questionScore,
            'unit_id' => $unitId > 0 ? $unitId : null,
        ];
    }

    $startedAt = trim((string)($session['started_at'] ?? ''));
    $finishedAt = api_question_attempt_now_local();
    $insert = $pdo->prepare(
        'INSERT INTO test_attempts'
        . '(student_id,subject_version_id,unit_id,mode,started_at,finished_at,'
        . 'questions_json,answers_json,scoring_json,total_points,max_points,percent,'
        . 'correct_count,partial_count,wrong_count,xp_earned,hearts_spent,duration_seconds,'
        . 'settings_snapshot_json) '
        . 'VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
    );
    $insert->execute([
        $studentId,
        $subjectVersionId,
        $unitId > 0 ? $unitId : null,
        $mode,
        $startedAt !== '' ? $startedAt : $finishedAt,
        $finishedAt,
        json_encode($questionList, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
        json_encode($answersBySource, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
        json_encode($scoring, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
        (float)($score['score_points'] ?? 0),
        (float)($score['total_questions'] ?? 0),
        (float)($score['score_percent_exact'] ?? $score['score_percent'] ?? 0),
        (int)($score['correct_answers'] ?? 0),
        (int)($score['partial_answers'] ?? 0),
        (int)($score['wrong_answers'] ?? 0),
        $xpEarned,
        $heartsSpent,
        $duration,
        json_encode($policy, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
    ]);
    $attemptId = (int)$pdo->lastInsertId();

    if ($attemptId > 0 && api_activity_table_exists($pdo, 'attempt_answers')) {
        $answerInsert = $pdo->prepare(
            'INSERT INTO attempt_answers'
            . '(attempt_id,student_id,subject_version_id,unit_id,mode,question_id,'
            . 'question_type,answer_json,score,is_correct,created_at) '
            . 'VALUES(?,?,?,?,?,?,?,?,?,?,?)',
        );
        foreach ($scoring as $item) {
            $qid = (int)$item['question_id'];
            $answer = $answersBySource[(string)$qid] ?? null;
            $itemScore = max(0.0, min(1.0, (float)$item['score']));
            $answerInsert->execute([
                $attemptId,
                $studentId,
                $subjectVersionId,
                $unitId > 0 ? $unitId : null,
                $mode,
                $qid,
                (string)$item['type'],
                json_encode($answer, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
                $itemScore,
                $itemScore >= 0.999 ? 1 : 0,
                $finishedAt,
            ]);
            if ($unitId > 0) {
                api_question_attempt_upsert_question_state(
                    $pdo,
                    $studentId,
                    $unitId,
                    $qid,
                    $itemScore,
                    $attemptId,
                    $finishedAt,
                );
            }
        }
    }

    if (api_activity_table_exists($pdo, 'exam_sessions')) {
        $exam = $pdo->prepare(
            'INSERT INTO exam_sessions'
            . '(student_id,subject_version_id,unit_id,mode,started_at,ended_at,duration_seconds,'
            . 'question_count,correct_count,xp_earned,hearts_spent,created_at) '
            . 'VALUES(?,?,?,?,?,?,?,?,?,?,?,?)',
        );
        $exam->execute([
            $studentId,
            $subjectVersionId,
            $unitId > 0 ? $unitId : null,
            $mode,
            $startedAt !== '' ? $startedAt : $finishedAt,
            $finishedAt,
            $duration,
            (int)($score['total_questions'] ?? 0),
            (int)($score['correct_answers'] ?? 0),
            $xpEarned,
            $heartsSpent,
            $finishedAt,
        ]);
    }

    $today = api_question_attempt_update_daily_time(
        $pdo,
        $studentId,
        $duration,
        (int)($score['total_questions'] ?? 0),
    );

    return [
        'attempt_id' => $attemptId > 0 ? $attemptId : null,
        'duration_seconds' => $duration,
        'today' => $today,
    ];
}
