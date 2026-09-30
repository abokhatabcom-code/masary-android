<?php
declare(strict_types=1);

final class QuestionRewardPolicyTestError extends RuntimeException
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
    throw new QuestionRewardPolicyTestError($code, $status, $message);
}

function api_server_secret(): string
{
    return 'phase136-reward-policy-test-secret';
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

require_once __DIR__ . '/../public_html/api/_question_result.php';

function reward_check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$pdo = new PDO('sqlite::memory:');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

$pdo->exec("CREATE TABLE api_activity_sessions(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    public_session_id TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    subject_version_id INTEGER NOT NULL,
    unit_id INTEGER,
    lesson_id INTEGER,
    activity_type TEXT NOT NULL,
    activity_mode TEXT NOT NULL,
    source TEXT NOT NULL,
    guide_step_id INTEGER,
    status TEXT NOT NULL,
    idempotency_key_hash TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    request_json TEXT NOT NULL,
    response_json TEXT NOT NULL,
    destination TEXT NOT NULL,
    heart_debited INTEGER NOT NULL DEFAULT 0,
    gems_debited INTEGER NOT NULL DEFAULT 0,
    expires_at TEXT NOT NULL,
    started_at TEXT,
    completed_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
)");
$pdo->exec("CREATE TABLE choose_questions(
    id INTEGER PRIMARY KEY,
    subject_version_id INTEGER NOT NULL,
    question_text TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT NOT NULL,
    option_d TEXT NOT NULL,
    correct_answer TEXT NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 1
)");
$pdo->exec("CREATE TABLE api_activity_answers(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    public_session_id TEXT NOT NULL,
    question_id TEXT NOT NULL,
    idempotency_key_hash TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    answer_json TEXT NOT NULL,
    result_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(user_id,idempotency_key_hash),
    UNIQUE(user_id,public_session_id,question_id)
)");
$pdo->exec("CREATE TABLE api_activity_results(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    public_session_id TEXT NOT NULL,
    idempotency_key_hash TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    result_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(user_id,idempotency_key_hash),
    UNIQUE(user_id,public_session_id)
)");
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

for ($id = 1; $id <= 10; $id++) {
    $statement = $pdo->prepare(
        "INSERT INTO choose_questions VALUES(?,?,?,?,?,?,?,?,1)"
    );
    $statement->execute([
        $id,
        25,
        'السؤال ' . $id,
        'أ',
        'ب',
        'ج',
        'د',
        'a',
    ]);
}

$policy = api_test_policy_defaults();
$policy['learn_xp_max'] = 20.0;
$policy['pass_percent'] = 60;
$requestJson = json_encode([
    'subject_version_id' => 25,
    'unit_id' => null,
    'lesson_id' => null,
    'activity_type' => 'choose_test',
    'activity_mode' => 'learn',
    'source' => 'subject',
    '_test_policy' => [
        'version' => 1,
        'subject_version_id' => 25,
        'unit_id' => null,
        'settings' => $policy,
        'hash' => 'fixture',
    ],
], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);

$insertSession = $pdo->prepare(
    "INSERT INTO api_activity_sessions(
        public_session_id,user_id,subject_version_id,unit_id,lesson_id,
        activity_type,activity_mode,source,guide_step_id,status,
        idempotency_key_hash,request_hash,request_json,response_json,destination,
        heart_debited,gems_debited,expires_at,started_at,completed_at,created_at,updated_at
    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
);
$insertSession->execute([
    'reward-seven-of-ten-001',
    42,
    25,
    null,
    null,
    'choose_test',
    'learn',
    'subject',
    null,
    'in_progress',
    str_repeat('a', 64),
    str_repeat('b', 64),
    $requestJson,
    '{}',
    'activity_session_pending_ui',
    0,
    0,
    '2099-01-01 00:00:00',
    null,
    null,
    '2026-09-29 00:00:00',
    '2026-09-29 00:00:00',
]);

$insertAnswer = $pdo->prepare(
    "INSERT INTO api_activity_answers(
        user_id,public_session_id,question_id,idempotency_key_hash,request_hash,
        answer_json,result_json,created_at,updated_at
    ) VALUES(?,?,?,?,?,?,?,?,?)"
);
for ($index = 1; $index <= 10; $index++) {
    $insertAnswer->execute([
        42,
        'reward-seven-of-ten-001',
        str_pad((string)$index, 32, '0', STR_PAD_LEFT),
        hash('sha256', 'key-' . $index),
        hash('sha256', 'req-' . $index),
        '{}',
        json_encode([
            'correct' => $index <= 7,
            'score' => $index <= 7 ? 1.0 : 0.0,
        ], JSON_THROW_ON_ERROR),
        '2026-09-29 00:01:00',
        '2026-09-29 00:01:00',
    ]);
}

$result = api_question_result_finish(
    $pdo,
    ['user_id' => 42],
    ['session_id' => 'reward-seven-of-ten-001'],
    'reward-seven-of-ten-finish-key-001',
);

reward_check($result['result']['score_percent'] === 70, 'Expected a 70 percent result.');
reward_check($result['result']['passed'] === true, '70 percent must pass the configured 60 threshold.');
reward_check(
    abs((float)$result['result']['xp_earned'] - 14.0) < 0.0001,
    '70 percent with learn_xp_max=20 must award exactly 14 XP.',
);
reward_check(
    abs((float)$pdo->query(
        "SELECT global_xp FROM student_profile_stats WHERE student_id=42"
    )->fetchColumn() - 14.0) < 0.0001,
    'Global XP must be exactly 14.',
);
reward_check(
    abs((float)$pdo->query(
        "SELECT subject_xp FROM student_subject_state WHERE student_id=42 AND subject_version_id=25"
    )->fetchColumn() - 14.0) < 0.0001,
    'Subject XP must be exactly 14.',
);
reward_check(
    (int)($result['confirmed_delta']['subjects'][0]['points'] ?? -1) === 14,
    'Confirmed subject delta must expose the new 14 points.',
);

$replay = api_question_result_finish(
    $pdo,
    ['user_id' => 42],
    ['session_id' => 'reward-seven-of-ten-001'],
    'reward-seven-of-ten-finish-key-001',
);
reward_check($replay['replayed'] === true, 'Same finish key must replay.');
reward_check(
    abs((float)$pdo->query(
        "SELECT global_xp FROM student_profile_stats WHERE student_id=42"
    )->fetchColumn() - 14.0) < 0.0001,
    'Replay must not award global XP twice.',
);
reward_check(
    abs((float)$pdo->query(
        "SELECT subject_xp FROM student_subject_state WHERE student_id=42 AND subject_version_id=25"
    )->fetchColumn() - 14.0) < 0.0001,
    'Replay must not award subject XP twice.',
);

echo "Question reward policy tests passed.\n";
