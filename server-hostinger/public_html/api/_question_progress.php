<?php
declare(strict_types=1);

function api_question_progress_driver(PDO $pdo): string
{
    return strtolower((string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME));
}

function api_question_progress_insert_ignore(
    PDO $pdo,
    string $table,
    array $columns,
    array $values,
): void {
    $names = implode(',', $columns);
    $marks = implode(',', array_fill(0, count($columns), '?'));
    $prefix = api_question_progress_driver($pdo) === 'sqlite'
        ? 'INSERT OR IGNORE INTO '
        : 'INSERT IGNORE INTO ';
    $pdo->prepare($prefix . $table . ' (' . $names . ') VALUES (' . $marks . ')')
        ->execute($values);
}

function api_question_progress_require_tables(PDO $pdo): void
{
    foreach (['student_profile_stats', 'student_subject_state', 'student_unit_state'] as $table) {
        if (!api_activity_table_exists($pdo, $table)) {
            api_error(
                'progress_schema_missing',
                'جداول تقدم الطالب غير مكتملة في هذه البيئة.',
                503,
            );
        }
    }
}

function api_question_progress_profile(PDO $pdo, int $studentId): array
{
    api_question_progress_insert_ignore(
        $pdo,
        'student_profile_stats',
        ['student_id', 'global_xp', 'gems', 'streak_days'],
        [$studentId, 0, 0, 0],
    );
    $statement = $pdo->prepare(
        'SELECT * FROM student_profile_stats WHERE student_id=? LIMIT 1',
    );
    $statement->execute([$studentId]);
    return $statement->fetch(PDO::FETCH_ASSOC) ?: [];
}

function api_question_progress_subject(
    PDO $pdo,
    int $studentId,
    int $subjectVersionId,
): array {
    api_question_progress_insert_ignore(
        $pdo,
        'student_subject_state',
        ['student_id', 'subject_version_id', 'subject_xp', 'hearts'],
        [$studentId, $subjectVersionId, 0, 3],
    );
    $statement = $pdo->prepare(
        'SELECT * FROM student_subject_state '
        . 'WHERE student_id=? AND subject_version_id=? LIMIT 1',
    );
    $statement->execute([$studentId, $subjectVersionId]);
    return $statement->fetch(PDO::FETCH_ASSOC) ?: [];
}

function api_question_progress_unit(PDO $pdo, int $studentId, int $unitId): array
{
    api_question_progress_insert_ignore(
        $pdo,
        'student_unit_state',
        ['student_id', 'unit_id'],
        [$studentId, $unitId],
    );
    $statement = $pdo->prepare(
        'SELECT * FROM student_unit_state WHERE student_id=? AND unit_id=? LIMIT 1',
    );
    $statement->execute([$studentId, $unitId]);
    return $statement->fetch(PDO::FETCH_ASSOC) ?: [];
}

function api_question_progress_node(
    PDO $pdo,
    int $studentId,
    int $contentNodeId,
): ?array {
    if ($contentNodeId <= 0 || !api_activity_table_exists($pdo, 'student_content_node_state')) {
        return null;
    }
    api_question_progress_insert_ignore(
        $pdo,
        'student_content_node_state',
        ['student_id', 'content_node_id'],
        [$studentId, $contentNodeId],
    );
    $statement = $pdo->prepare(
        'SELECT * FROM student_content_node_state '
        . 'WHERE student_id=? AND content_node_id=? LIMIT 1',
    );
    $statement->execute([$studentId, $contentNodeId]);
    return $statement->fetch(PDO::FETCH_ASSOC) ?: null;
}

function api_question_progress_review_cap(PDO $pdo, int $subjectVersionId): float
{
    try {
        $statement = $pdo->prepare(
            'SELECT review_progress_cap_points FROM subject_versions WHERE id=? LIMIT 1',
        );
        $statement->execute([$subjectVersionId]);
        $value = $statement->fetchColumn();
        if ($value !== false && (float)$value > 0) {
            return (float)$value;
        }
    } catch (Throwable) {
    }
    return 150.0;
}

function api_question_progress_mode(array $session): string
{
    $activityType = (string)($session['activity_type'] ?? '');
    $activityMode = (string)($session['activity_mode'] ?? '');

    if ($activityType === 'speed_test' || $activityMode === 'speed') {
        return 'speed';
    }
    if ($activityType === 'smart_review' || $activityType === 'review') {
        return 'mistakes';
    }
    if (in_array($activityMode, ['review', 'practice'], true)) {
        return 'review';
    }
    return 'learn';
}

function api_question_progress_xp_max(array $policy, string $mode): float
{
    return match ($mode) {
        'speed' => 0.0,
        'mistakes' => max(0.0, (float)($policy['mistakes_xp_total'] ?? 1.0)),
        'review' => max(0.0, (float)($policy['review_xp_max'] ?? 5.0)),
        default => max(0.0, (float)($policy['learn_xp_max'] ?? 20.0)),
    };
}

function api_question_progress_level(float $xp, float $step): array
{
    $step = max(1.0, $step);
    $safeXp = max(0.0, $xp);
    $level = min(10, (int)floor($safeXp / $step) + 1);
    $levelStart = ($level - 1) * $step;
    $progress = $level >= 10
        ? 100
        : (int)round((($safeXp - $levelStart) / $step) * 100);
    return [
        'level' => max(1, $level),
        'progress_percent' => max(0, min(100, $progress)),
        'next_xp' => $level >= 10 ? (int)round(10 * $step) : (int)round($level * $step),
    ];
}

function api_question_progress_content_node_id(PDO $pdo, array $session): int
{
    if ((int)($session['lesson_id'] ?? 0) <= 0) {
        return 0;
    }
    if (function_exists('api_question_session_lesson_content_node_id')) {
        return (int)(api_question_session_lesson_content_node_id($pdo, $session) ?? 0);
    }
    return 0;
}

function api_question_progress_apply(
    PDO $pdo,
    int $studentId,
    array $session,
    array $score,
    array $policy,
): array {
    api_question_progress_require_tables($pdo);

    $subjectVersionId = (int)($session['subject_version_id'] ?? 0);
    $unitId = (int)($session['unit_id'] ?? 0);
    if ($subjectVersionId <= 0 || $unitId <= 0) {
        api_error(
            'progress_scope_missing',
            'لا يمكن إثبات نطاق التقدم لهذه الجلسة.',
            503,
        );
    }

    $profile = api_question_progress_profile($pdo, $studentId);
    $subject = api_question_progress_subject($pdo, $studentId, $subjectVersionId);
    $unit = api_question_progress_unit($pdo, $studentId, $unitId);
    $contentNodeId = api_question_progress_content_node_id($pdo, $session);
    $node = api_question_progress_node($pdo, $studentId, $contentNodeId);

    $mode = api_question_progress_mode($session);
    $xpMax = api_question_progress_xp_max($policy, $mode);
    $percentExact = max(
        0.0,
        min(100.0, (float)($score['score_percent_exact'] ?? $score['score_percent'] ?? 0)),
    );
    $xpEarned = round(($percentExact / 100.0) * $xpMax, 4);
    $now = gmdate('Y-m-d H:i:s');
    $reviewCap = api_question_progress_review_cap($pdo, $subjectVersionId);
    $correct = max(0, (int)($score['correct_answers'] ?? 0));
    $total = max(0, (int)($score['total_questions'] ?? 0));

    $effectiveMode = $mode === 'mistakes' ? 'review' : $mode;
    if ($effectiveMode === 'learn') {
        if ((int)($unit['learn_attempt_done'] ?? 0) !== 1) {
            $update = $pdo->prepare(
                'UPDATE student_unit_state SET learn_attempt_done=1,learn_xp_earned=?,'
                . 'last_score_correct=?,last_score_total=?,updated_at=? '
                . 'WHERE student_id=? AND unit_id=?',
            );
            $update->execute([$xpEarned, $correct, $total, $now, $studentId, $unitId]);
        }

        if ($node !== null) {
            if ((int)($node['learn_attempt_done'] ?? 0) !== 1) {
                $update = $pdo->prepare(
                    'UPDATE student_content_node_state SET learn_attempt_done=1,learn_xp_earned=?,'
                    . 'last_score_correct=?,last_score_total=?,updated_at=? '
                    . 'WHERE student_id=? AND content_node_id=?',
                );
                $update->execute([
                    $xpEarned,
                    $correct,
                    $total,
                    $now,
                    $studentId,
                    $contentNodeId,
                ]);
            } else {
                $effectiveMode = 'review';
            }
        } elseif ((int)($unit['learn_attempt_done'] ?? 0) === 1) {
            $effectiveMode = 'review';
        }
    }

    if ($effectiveMode === 'review') {
        $newReview = min(
            $reviewCap,
            max(0.0, (float)($unit['review_xp_total'] ?? 0)) + $xpEarned,
        );
        $update = $pdo->prepare(
            'UPDATE student_unit_state SET review_xp_total=?,review_attempt_count=?,'
            . 'last_score_correct=?,last_score_total=?,updated_at=? '
            . 'WHERE student_id=? AND unit_id=?',
        );
        $update->execute([
            $newReview,
            max(0, (int)($unit['review_attempt_count'] ?? 0)) + 1,
            $correct,
            $total,
            $now,
            $studentId,
            $unitId,
        ]);

        if ($node !== null) {
            $newNodeReview = min(
                $reviewCap,
                max(0.0, (float)($node['review_xp_total'] ?? 0)) + $xpEarned,
            );
            $update = $pdo->prepare(
                'UPDATE student_content_node_state SET review_xp_total=?,review_attempt_count=?,'
                . 'last_score_correct=?,last_score_total=?,updated_at=? '
                . 'WHERE student_id=? AND content_node_id=?',
            );
            $update->execute([
                $newNodeReview,
                max(0, (int)($node['review_attempt_count'] ?? 0)) + 1,
                $correct,
                $total,
                $now,
                $studentId,
                $contentNodeId,
            ]);
        }
    }

    $newSubjectXp = max(0.0, (float)($subject['subject_xp'] ?? 0)) + $xpEarned;
    $pdo->prepare(
        'UPDATE student_subject_state SET subject_xp=? '
        . 'WHERE student_id=? AND subject_version_id=?',
    )->execute([$newSubjectXp, $studentId, $subjectVersionId]);

    $newGlobalXp = max(0.0, (float)($profile['global_xp'] ?? 0)) + $xpEarned;
    $pdo->prepare(
        'UPDATE student_profile_stats SET global_xp=? WHERE student_id=?',
    )->execute([$newGlobalXp, $studentId]);

    if (function_exists('student_streak_record_qualifying_activity')) {
        try {
            student_streak_record_qualifying_activity($pdo, $studentId, $profile);
        } catch (Throwable) {
        }
    }
    if (function_exists('set_last_activity_unit')) {
        try {
            set_last_activity_unit(
                $pdo,
                $studentId,
                $subjectVersionId,
                $unitId,
                $effectiveMode === 'review' ? 'review' : 'learn',
            );
        } catch (Throwable) {
        }
    }

    $subjectLevel = api_question_progress_level($newSubjectXp, 100.0);
    $globalLevel = api_question_progress_level($newGlobalXp, 300.0);
    $hearts = max(0, (int)($subject['hearts'] ?? 0));

    return [
        'xp_earned' => $xpEarned,
        'mode' => $mode,
        'effective_mode' => $effectiveMode,
        'confirmed_delta' => [
            'available' => true,
            'reason' => '',
            'student_id' => (string)$studentId,
            'profile' => [
                'global_xp' => (int)round($newGlobalXp),
                'level' => (int)$globalLevel['level'],
                'level_progress_percent' => (int)$globalLevel['progress_percent'],
                'level_next_xp' => (int)$globalLevel['next_xp'],
            ],
            'subjects' => [[
                'subject_version_id' => $subjectVersionId,
                'points' => (int)round($newSubjectXp),
                'level' => (int)$subjectLevel['level'],
                'level_progress_percent' => (int)$subjectLevel['progress_percent'],
                'hearts' => $hearts,
            ]],
            'server_version' => hash(
                'sha256',
                implode('|', [
                    (string)$studentId,
                    (string)$subjectVersionId,
                    number_format($newGlobalXp, 4, '.', ''),
                    number_format($newSubjectXp, 4, '.', ''),
                    $now,
                ]),
            ),
            'confirmed_at_epoch_millis' => (int)round(microtime(true) * 1000),
        ],
    ];
}
