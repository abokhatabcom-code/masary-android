<?php
declare(strict_types=1);

/**
 * Phase 13.6 — authoritative test policy shared by activity/session/answer/result.
 *
 * Source hierarchy mirrors the legacy admin test engine:
 * system defaults -> version_test_settings -> unit_test_settings.
 */
function api_test_policy_defaults(): array
{
    return [
        'questions_per_attempt' => 10,
        'allowed_types' => ['tf', 'mcq', 'fill', 'direct', 'match'],
        'allowed_difficulties' => ['easy', 'medium', 'hard'],
        'question_order' => 'random',
        'shuffle_mcq_options' => 1,
        'shuffle_match_right' => 1,
        'allow_back' => 1,
        'allow_skip' => 1,
        'show_feedback' => 'end',
        'reveal_answers' => 1,
        'tf_reason_only_on_false' => 1,
        'pass_percent' => 60,
        'timer_seconds' => 0,
        'learn_xp_max' => 20.0,
        'review_xp_max' => 5.0,
        'review_heart_cost' => 1,
        'show_mistakes_button_unit' => 1,
        'show_mistakes_button_result' => 1,
        'show_mistakes_button_achievements' => 1,
        'mistakes_heart_cost' => 1,
        'mistakes_xp_total' => 1.0,
        'result_show_pass_badge' => 1,
        'result_show_score' => 1,
        'result_show_counts_correct' => 1,
        'result_show_counts_partial' => 1,
        'result_show_counts_wrong' => 1,
        'result_show_xp' => 1,
        'result_show_hearts_spent' => 1,
        'result_show_retry_button' => 1,
        'result_show_back_button' => 1,
        'result_show_review_details' => 1,
        'active_time_enabled' => 1,
        'active_time_idle_seconds' => 45,
        'active_time_ping_interval' => 15,
    ];
}

function api_test_policy_merge(array $base, array $row): array
{
    if ($row === []) {
        return $base;
    }

    $types = is_array($row['allowed_types'] ?? null)
        ? $row['allowed_types']
        : json_decode((string)($row['allowed_types_json'] ?? '[]'), true);
    $difficulties = is_array($row['allowed_difficulties'] ?? null)
        ? $row['allowed_difficulties']
        : json_decode((string)($row['allowed_difficulties_json'] ?? '[]'), true);
    $out = $base;

    $out['questions_per_attempt'] = max(
        1,
        min(60, (int)($row['questions_per_attempt'] ?? $out['questions_per_attempt'])),
    );
    if (is_array($types) && $types !== []) {
        $out['allowed_types'] = array_values(array_unique(array_map('strval', $types)));
    }
    if (is_array($difficulties) && $difficulties !== []) {
        $out['allowed_difficulties'] = array_values(
            array_unique(array_map('strval', $difficulties)),
        );
    }

    $out['question_order'] =
        (string)($row['question_order'] ?? $out['question_order']) === 'fixed'
            ? 'fixed'
            : 'random';

    foreach ([
        'shuffle_mcq_options',
        'shuffle_match_right',
        'allow_back',
        'allow_skip',
        'reveal_answers',
        'tf_reason_only_on_false',
        'show_mistakes_button_unit',
        'show_mistakes_button_result',
        'show_mistakes_button_achievements',
        'result_show_pass_badge',
        'result_show_score',
        'result_show_counts_correct',
        'result_show_counts_partial',
        'result_show_counts_wrong',
        'result_show_xp',
        'result_show_hearts_spent',
        'result_show_retry_button',
        'result_show_back_button',
        'result_show_review_details',
        'active_time_enabled',
    ] as $key) {
        $out[$key] = (int)($row[$key] ?? $out[$key] ?? 0) === 1 ? 1 : 0;
    }

    $out['show_feedback'] = 'end';
    $out['pass_percent'] = max(
        1,
        min(100, (int)($row['pass_percent'] ?? $out['pass_percent'])),
    );
    $out['timer_seconds'] = max(
        0,
        min(3600, (int)($row['timer_seconds'] ?? $out['timer_seconds'])),
    );
    $out['learn_xp_max'] = max(
        0.0,
        (float)($row['learn_xp_max'] ?? $out['learn_xp_max']),
    );
    $out['review_xp_max'] = max(
        0.0,
        (float)($row['review_xp_max'] ?? $out['review_xp_max']),
    );
    $out['review_heart_cost'] = max(
        0,
        min(3, (int)($row['review_heart_cost'] ?? $out['review_heart_cost'])),
    );
    $out['mistakes_heart_cost'] = max(
        0,
        min(3, (int)($row['mistakes_heart_cost'] ?? $out['mistakes_heart_cost'])),
    );
    $out['mistakes_xp_total'] = max(
        0.0,
        (float)($row['mistakes_xp_total'] ?? $out['mistakes_xp_total']),
    );
    $out['active_time_idle_seconds'] = max(
        5,
        min(900, (int)($row['active_time_idle_seconds'] ?? $out['active_time_idle_seconds'])),
    );
    $out['active_time_ping_interval'] = max(
        5,
        min(60, (int)($row['active_time_ping_interval'] ?? $out['active_time_ping_interval'])),
    );
    return $out;
}

function api_test_policy_row(
    PDO $pdo,
    string $table,
    string $keyColumn,
    int $keyValue,
): array {
    if ($keyValue <= 0) {
        return [];
    }
    if (!in_array($table, ['version_test_settings', 'unit_test_settings'], true)) {
        return [];
    }
    try {
        $statement = $pdo->prepare(
            'SELECT * FROM ' . $table . ' WHERE ' . $keyColumn . '=? LIMIT 1',
        );
        $statement->execute([$keyValue]);
        return $statement->fetch(PDO::FETCH_ASSOC) ?: [];
    } catch (Throwable) {
        return [];
    }
}

function api_test_policy_effective(
    PDO $pdo,
    int $subjectVersionId,
    ?int $unitId,
): array {
    $policy = api_test_policy_defaults();
    $policy = api_test_policy_merge(
        $policy,
        api_test_policy_row(
            $pdo,
            'version_test_settings',
            'subject_version_id',
            $subjectVersionId,
        ),
    );
    if (($unitId ?? 0) > 0) {
        $policy = api_test_policy_merge(
            $policy,
            api_test_policy_row($pdo, 'unit_test_settings', 'unit_id', (int)$unitId),
        );
    }
    return $policy;
}

function api_test_policy_snapshot(
    PDO $pdo,
    int $subjectVersionId,
    ?int $unitId,
): array {
    $settings = api_test_policy_effective($pdo, $subjectVersionId, $unitId);
    $payload = [
        'version' => 1,
        'subject_version_id' => $subjectVersionId,
        'unit_id' => ($unitId ?? 0) > 0 ? (int)$unitId : null,
        'settings' => $settings,
    ];
    $payload['hash'] = hash(
        'sha256',
        json_encode(
            $payload,
            JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR,
        ),
    );
    return $payload;
}

function api_test_policy_for_session(PDO $pdo, array $session): array
{
    $request = json_decode((string)($session['request_json'] ?? ''), true);
    if (is_array($request)) {
        $snapshot = $request['_test_policy'] ?? null;
        if (is_array($snapshot) && is_array($snapshot['settings'] ?? null)) {
            return api_test_policy_merge(
                api_test_policy_defaults(),
                (array)$snapshot['settings'],
            );
        }
    }

    return api_test_policy_effective(
        $pdo,
        (int)($session['subject_version_id'] ?? 0),
        ((int)($session['unit_id'] ?? 0)) ?: null,
    );
}

function api_test_policy_heart_cost(array $policy, array $request): int
{
    $activityType = trim((string)($request['activity_type'] ?? ''));
    $activityMode = trim((string)($request['activity_mode'] ?? ''));

    if ($activityType === 'smart_review') {
        return max(0, min(3, (int)($policy['mistakes_heart_cost'] ?? 1)));
    }
    if ($activityMode === 'speed' || $activityType === 'speed_test') {
        return 0;
    }
    if ($activityType === 'review' || $activityMode === 'review') {
        return max(0, min(3, (int)($policy['review_heart_cost'] ?? 1)));
    }
    return 0;
}

function api_test_policy_public(array $policy): array
{
    return [
        'allow_back' => (int)($policy['allow_back'] ?? 0) === 1,
        'allow_skip' => (int)($policy['allow_skip'] ?? 0) === 1,
        'reveal_answers' => (int)($policy['reveal_answers'] ?? 0) === 1,
        'tf_reason_only_on_false' => (int)($policy['tf_reason_only_on_false'] ?? 0) === 1,
        'pass_percent' => (int)($policy['pass_percent'] ?? 60),
        'timer_seconds' => (int)($policy['timer_seconds'] ?? 0),
        'active_time' => [
            'enabled' => (int)($policy['active_time_enabled'] ?? 0) === 1,
            'idle_seconds' => (int)($policy['active_time_idle_seconds'] ?? 45),
            'ping_interval' => (int)($policy['active_time_ping_interval'] ?? 15),
        ],
        'result' => [
            'show_pass_badge' => (int)($policy['result_show_pass_badge'] ?? 1) === 1,
            'show_score' => (int)($policy['result_show_score'] ?? 1) === 1,
            'show_counts_correct' => (int)($policy['result_show_counts_correct'] ?? 1) === 1,
            'show_counts_partial' => (int)($policy['result_show_counts_partial'] ?? 1) === 1,
            'show_counts_wrong' => (int)($policy['result_show_counts_wrong'] ?? 1) === 1,
            'show_xp' => (int)($policy['result_show_xp'] ?? 1) === 1,
            'show_hearts_spent' => (int)($policy['result_show_hearts_spent'] ?? 1) === 1,
            'show_retry_button' => (int)($policy['result_show_retry_button'] ?? 1) === 1,
            'show_back_button' => (int)($policy['result_show_back_button'] ?? 1) === 1,
            'show_review_details' => (int)($policy['result_show_review_details'] ?? 1) === 1,
            'show_mistakes_button' =>
                (int)($policy['show_mistakes_button_result'] ?? 1) === 1,
        ],
    ];
}
