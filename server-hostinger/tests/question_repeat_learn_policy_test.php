<?php
declare(strict_types=1);

final class RepeatLearnPolicyTestError extends RuntimeException
{
    public function __construct(
        public readonly string $apiCode,
        public readonly int $status,
        string $message,
    ) {
        parent::__construct($message);
    }
}

function api_error(string $code, string $message, int $status = 400): never
{
    throw new RepeatLearnPolicyTestError($code, $status, $message);
}

function api_server_secret(): string
{
    return 'phase136-repeat-learn-policy-test-secret';
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

require_once __DIR__ . '/../public_html/api/_question_result.php';

function repeat_check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$pdo = new PDO('sqlite::memory:');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

$pdo->exec("CREATE TABLE student_profile_stats(
    student_id INTEGER PRIMARY KEY,
    global_xp REAL NOT NULL DEFAULT 0,
    gems INTEGER NOT NULL DEFAULT 0,
    streak_days INTEGER NOT NULL DEFAULT 0
)");
$pdo->exec("CREATE TABLE student_subject_state(
    student_id INTEGER NOT NULL,
    subject_version_id INTEGER NOT NULL,
    subject_xp REAL NOT NULL DEFAULT 0,
    hearts INTEGER NOT NULL DEFAULT 3,
    PRIMARY KEY(student_id,subject_version_id)
)");
$pdo->exec("CREATE TABLE student_unit_state(
    student_id INTEGER NOT NULL,
    unit_id INTEGER NOT NULL,
    learn_attempt_done INTEGER NOT NULL DEFAULT 0,
    learn_xp_earned REAL NOT NULL DEFAULT 0,
    review_xp_total REAL NOT NULL DEFAULT 0,
    review_attempt_count INTEGER NOT NULL DEFAULT 0,
    last_score_correct INTEGER NOT NULL DEFAULT 0,
    last_score_total INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT,
    PRIMARY KEY(student_id,unit_id)
)");

$policy = api_test_policy_defaults();
$policy['learn_xp_max'] = 20.0;
$policy['review_xp_max'] = 5.0;

$session = [
    'user_id' => 42,
    'subject_version_id' => 25,
    'unit_id' => 64,
    'lesson_id' => null,
    'activity_type' => 'lesson_practice',
    'activity_mode' => 'learn',
];
$score = [
    'correct_answers' => 8,
    'total_questions' => 10,
    'score_percent_exact' => 80.0,
    'score_percent' => 80,
];

$first = api_question_progress_apply($pdo, 42, $session, $score, $policy);
repeat_check(
    abs((float)$first['xp_earned'] - 16.0) < 0.0001,
    'First 80 percent learn attempt must award 16 of learn_xp_max=20.',
);
repeat_check(
    (int)$pdo->query(
        'SELECT learn_attempt_done FROM student_unit_state WHERE student_id=42 AND unit_id=64'
    )->fetchColumn() === 1,
    'First learn attempt was not marked complete.',
);

$second = api_question_progress_apply($pdo, 42, $session, $score, $policy);
repeat_check(
    ($second['effective_mode'] ?? '') === 'review',
    'Repeated learn attempt did not switch to review mode.',
);
repeat_check(
    abs((float)$second['xp_earned'] - 4.0) < 0.0001,
    'Repeated 80 percent learn must use review_xp_max=5 and award 4 XP.',
);
repeat_check(
    abs((float)$pdo->query(
        'SELECT subject_xp FROM student_subject_state WHERE student_id=42 AND subject_version_id=25'
    )->fetchColumn() - 20.0) < 0.0001,
    'Subject XP after learn + repeated review must be 20.',
);
repeat_check(
    abs((float)$pdo->query(
        'SELECT global_xp FROM student_profile_stats WHERE student_id=42'
    )->fetchColumn() - 20.0) < 0.0001,
    'Global XP after learn + repeated review must be 20.',
);
repeat_check(
    abs((float)$pdo->query(
        'SELECT review_xp_total FROM student_unit_state WHERE student_id=42 AND unit_id=64'
    )->fetchColumn() - 4.0) < 0.0001,
    'Repeated learn XP was not recorded as review progress.',
);

$mistakesPolicy = $policy;
$mistakesPolicy['mistakes_xp_total'] = 1.0;
$mistakesSession = array_replace($session, [
    'activity_type' => 'smart_review',
    'activity_mode' => 'review',
    'source' => 'review',
]);
$perfectScore = array_replace($score, [
    'correct_answers' => 10,
    'score_percent_exact' => 100.0,
    'score_percent' => 100,
]);
$mistakes = api_question_progress_apply(
    $pdo,
    42,
    $mistakesSession,
    $perfectScore,
    $mistakesPolicy,
);
repeat_check(
    abs((float)$mistakes['xp_earned'] - 1.0) < 0.0001,
    'Smart mistakes review must use mistakes_xp_total=1.',
);
repeat_check(
    abs((float)$pdo->query(
        'SELECT subject_xp FROM student_subject_state WHERE student_id=42 AND subject_version_id=25'
    )->fetchColumn() - 21.0) < 0.0001,
    'Mistakes review did not add exactly one subject XP.',
);

echo "Repeat learn policy tests passed.\n";
