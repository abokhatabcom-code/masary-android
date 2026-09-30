<?php
declare(strict_types=1);

final class QuestionResultTestError extends RuntimeException
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
    throw new QuestionResultTestError($code, $status, $message);
}

function api_server_secret(): string
{
    return 'phase13-result-test-secret';
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

require_once __DIR__ . '/../public_html/api/_question_result.php';

function result_check(bool $condition, string $message): void
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

$pdo->exec("INSERT INTO choose_questions VALUES
    (1,12,'السؤال الأول','أ','ب','ج','د','a',1),
    (2,12,'السؤال الثاني','أ','ب','ج','د','b',1)");

$insertSession = $pdo->prepare(
    "INSERT INTO api_activity_sessions(
        public_session_id,user_id,subject_version_id,unit_id,lesson_id,
        activity_type,activity_mode,source,guide_step_id,status,
        idempotency_key_hash,request_hash,request_json,response_json,destination,
        heart_debited,gems_debited,expires_at,started_at,completed_at,created_at,updated_at
    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
);

foreach ([
    ['activity-result-complete-001', 42, 'in_progress'],
    ['activity-result-incomplete-001', 42, 'in_progress'],
] as [$sessionId, $userId, $status]) {
    $insertSession->execute([
        $sessionId,
        $userId,
        12,
        null,
        null,
        'choose_test',
        'learn',
        'subject',
        null,
        $status,
        str_repeat('a', 64),
        str_repeat('b', 64),
        '{}',
        '{}',
        'activity_session_pending_ui',
        0,
        0,
        '2099-01-01 00:00:00',
        null,
        null,
        '2026-09-28 00:00:00',
        '2026-09-28 00:00:00',
    ]);
}

$insertAnswer = $pdo->prepare(
    "INSERT INTO api_activity_answers(
        user_id,public_session_id,question_id,idempotency_key_hash,request_hash,
        answer_json,result_json,created_at,updated_at
    ) VALUES(?,?,?,?,?,?,?,?,?)"
);

$insertAnswer->execute([
    42,
    'activity-result-complete-001',
    str_repeat('1', 32),
    str_repeat('c', 64),
    str_repeat('d', 64),
    '{}',
    json_encode(['correct' => true], JSON_THROW_ON_ERROR),
    '2026-09-28 00:01:00',
    '2026-09-28 00:01:00',
]);
$insertAnswer->execute([
    42,
    'activity-result-complete-001',
    str_repeat('2', 32),
    str_repeat('e', 64),
    str_repeat('f', 64),
    '{}',
    json_encode(['correct' => false], JSON_THROW_ON_ERROR),
    '2026-09-28 00:02:00',
    '2026-09-28 00:02:00',
]);
$insertAnswer->execute([
    42,
    'activity-result-incomplete-001',
    str_repeat('3', 32),
    str_repeat('1', 64),
    str_repeat('2', 64),
    '{}',
    json_encode(['correct' => true], JSON_THROW_ON_ERROR),
    '2026-09-28 00:03:00',
    '2026-09-28 00:03:00',
]);

$result = api_question_result_finish(
    $pdo,
    ['user_id' => 42],
    ['session_id' => 'activity-result-complete-001'],
    'question-result-key-00000001',
);

result_check($result['status'] === 'completed', 'Session did not complete.');
result_check($result['replayed'] === false, 'First completion was incorrectly replayed.');
result_check($result['result']['correct_answers'] === 1, 'Correct answer count is wrong.');
result_check($result['result']['incorrect_answers'] === 1, 'Incorrect answer count is wrong.');
result_check($result['result']['total_questions'] === 2, 'Result total is wrong.');
result_check($result['result']['score_percent'] === 50, 'Score percent is wrong.');
result_check(
    ($result['confirmed_delta']['available'] ?? false) === true,
    'Confirmed reward delta must be available after authoritative finish.',
);
result_check(
    abs((float)($result['result']['xp_earned'] ?? -1) - 10.0) < 0.0001,
    '50 percent with learn_xp_max=20 must award 10 XP.',
);
result_check(
    abs((float)$pdo->query(
        'SELECT global_xp FROM student_profile_stats WHERE student_id=42',
    )->fetchColumn() - 10.0) < 0.0001,
    'Global XP was not updated.',
);
result_check(
    abs((float)$pdo->query(
        'SELECT subject_xp FROM student_subject_state WHERE student_id=42 AND subject_version_id=12',
    )->fetchColumn() - 10.0) < 0.0001,
    'Subject XP was not updated.',
);
result_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_results')->fetchColumn() === 1,
    'Final result was not stored exactly once.',
);
result_check(
    (string)$pdo->query(
        "SELECT status FROM api_activity_sessions WHERE public_session_id='activity-result-complete-001'",
    )->fetchColumn() === 'completed',
    'Session status was not marked completed.',
);
result_check(
    (string)$pdo->query(
        "SELECT completed_at FROM api_activity_sessions WHERE public_session_id='activity-result-complete-001'",
    )->fetchColumn() !== '',
    'Session completed_at was not stored.',
);

$pdo->exec(
    "UPDATE api_activity_sessions SET expires_at='2000-01-01 00:00:00' "
    . "WHERE public_session_id='activity-result-complete-001'"
);
$replayed = api_question_result_finish(
    $pdo,
    ['user_id' => 42],
    ['session_id' => 'activity-result-complete-001'],
    'question-result-key-00000001',
);
result_check(
    $replayed['replayed'] === true,
    'Stored final result must replay even after activity expiry.',
);
result_check(
    abs((float)$pdo->query(
        'SELECT global_xp FROM student_profile_stats WHERE student_id=42',
    )->fetchColumn() - 10.0) < 0.0001,
    'Replayed finish must not award XP twice.',
);

$secondKey = api_question_result_finish(
    $pdo,
    ['user_id' => 42],
    ['session_id' => 'activity-result-complete-001'],
    'question-result-key-00000002',
);
result_check($secondKey['replayed'] === true, 'Same completed session should replay stored result.');
result_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_results')->fetchColumn() === 1,
    'Second finish key created a duplicate session result.',
);

try {
    api_question_result_finish(
        $pdo,
        ['user_id' => 42],
        ['session_id' => 'activity-result-incomplete-001'],
        'question-result-key-00000003',
    );
    throw new RuntimeException('Expected incomplete session to fail.');
} catch (QuestionResultTestError $error) {
    result_check(
        $error->apiCode === 'session_incomplete' && $error->status === 409,
        'Incomplete session returned the wrong error.',
    );
}

try {
    api_question_result_finish(
        $pdo,
        ['user_id' => 43],
        ['session_id' => 'activity-result-complete-001'],
        'question-result-key-00000004',
    );
    throw new RuntimeException('Expected cross-account finish to fail.');
} catch (QuestionResultTestError $error) {
    result_check(
        $error->apiCode === 'activity_session_not_found' && $error->status === 404,
        'Cross-account result lookup leaked session ownership.',
    );
}

$endpointSource = file_get_contents(
    __DIR__ . '/../public_html/api/v1/student/activity/finish.php',
);
result_check($endpointSource !== false, 'Unable to read question finish endpoint.');
foreach ([
    "api_require_method('POST')",
    'api_authenticate_access_token',
    'api_question_result_finish',
    'HTTP_IDEMPOTENCY_KEY',
    "api_rate_limit('student_question_finish'",
] as $required) {
    result_check(
        str_contains((string)$endpointSource, $required),
        "Question finish endpoint is missing: {$required}",
    );
}

echo "Question session result tests passed.\n";
