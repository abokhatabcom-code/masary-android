<?php
declare(strict_types=1);

final class QuestionAnswerTestError extends RuntimeException
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
    throw new QuestionAnswerTestError($code, $status, $message);
}

function api_server_secret(): string
{
    return 'phase13-answer-test-secret';
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

require_once __DIR__ . '/../public_html/api/_question_answer.php';

function answer_check(bool $condition, string $message): void
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
$pdo->exec("CREATE TABLE api_activity_answer_operations(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    public_session_id TEXT NOT NULL,
    question_id TEXT NOT NULL,
    idempotency_key_hash TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    result_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE(user_id,idempotency_key_hash)
)");

$pdo->exec("INSERT INTO choose_questions VALUES
    (1,12,'ما ناتج 2 + 2؟','1','2','4','5','c',1)");

$stmt = $pdo->prepare(
    "INSERT INTO api_activity_sessions(
        public_session_id,user_id,subject_version_id,unit_id,lesson_id,
        activity_type,activity_mode,source,guide_step_id,status,
        idempotency_key_hash,request_hash,request_json,response_json,destination,
        heart_debited,gems_debited,expires_at,started_at,completed_at,created_at,updated_at
    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
);
$stmt->execute([
    'activity-session-answer-001',
    42,
    12,
    null,
    null,
    'choose_test',
    'practice',
    'subject',
    null,
    'created',
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

$package = api_question_session_package($pdo, ['user_id' => 42], 'activity-session-answer-001');
$question = $package['questions'][0];
$correctOption = null;
$wrongOption = null;
foreach ($question['payload']['options'] as $option) {
    if ($option['text'] === '4') {
        $correctOption = $option['id'];
    } elseif ($wrongOption === null) {
        $wrongOption = $option['id'];
    }
}
answer_check(is_string($correctOption) && $correctOption !== '', 'Correct option token was not found.');
answer_check(is_string($wrongOption) && $wrongOption !== '', 'Wrong option token was not found.');

$payload = [
    'session_id' => 'activity-session-answer-001',
    'question_id' => $question['id'],
    'answer' => [
        'kind' => 'choice',
        'option_id' => $correctOption,
    ],
];
$key = 'question-answer-key-00000001';
$result = api_question_answer_submit($pdo, ['user_id' => 42], $payload, $key);

answer_check($result['accepted'] === true, 'Answer was not accepted.');
answer_check($result['correct'] === true, 'Correct answer was graded incorrectly.');
answer_check($result['replayed'] === false, 'First answer was incorrectly marked replayed.');
answer_check($result['progress']['answered'] === 1, 'Answered count is wrong.');
answer_check($result['progress']['total_questions'] === 1, 'Total count is wrong.');
answer_check($result['progress']['all_answered'] === true, 'Single question should be fully answered.');
answer_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_answers')->fetchColumn() === 1,
    'First answer was not stored exactly once.',
);
answer_check(
    (string)$pdo->query("SELECT status FROM api_activity_sessions WHERE public_session_id='activity-session-answer-001'")->fetchColumn()
        === 'in_progress',
    'Answer did not move the server session to in_progress.',
);

$replayed = api_question_answer_submit($pdo, ['user_id' => 42], $payload, $key);
answer_check($replayed['replayed'] === true, 'Same idempotency key was not replayed.');
answer_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_answers')->fetchColumn() === 1,
    'Idempotent replay created a duplicate row.',
);

$secondKeySameAnswer = api_question_answer_submit(
    $pdo,
    ['user_id' => 42],
    $payload,
    'question-answer-key-00000002',
);
answer_check(
    $secondKeySameAnswer['replayed'] === true,
    'Same semantic answer with another key should reuse the stored question result.',
);
answer_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_answers')->fetchColumn() === 1,
    'Second key created a duplicate answer.',
);

$revisedPayload = array_replace_recursive($payload, [
    'answer' => ['kind' => 'choice', 'option_id' => $wrongOption],
]);
$revised = api_question_answer_submit(
    $pdo,
    ['user_id' => 42],
    $revisedPayload,
    'question-answer-key-00000003',
);
answer_check($revised['accepted'] === true, 'Admin-enabled answer revision was not accepted.');
answer_check($revised['correct'] === false, 'Revised wrong answer was not stored as current.');
answer_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_answers')->fetchColumn() === 1,
    'Revision created a duplicate current answer row.',
);
answer_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_answer_operations')->fetchColumn() >= 3,
    'Revision operation history was not preserved.',
);

// A delayed retry from the original operation must replay its original response
// without rolling the current answer back.
$oldRetry = api_question_answer_submit($pdo, ['user_id' => 42], $payload, $key);
answer_check($oldRetry['replayed'] === true, 'Old operation retry was not replayed.');
answer_check($oldRetry['correct'] === true, 'Old operation did not replay its original result.');
$currentResultJson = (string)$pdo->query(
    "SELECT result_json FROM api_activity_answers "
    . "WHERE public_session_id='activity-session-answer-001'"
)->fetchColumn();
$currentResult = json_decode($currentResultJson, true);
answer_check(
    is_array($currentResult) && ($currentResult['correct'] ?? true) === false,
    'Delayed old retry rolled the current revised answer backward.',
);

$noBackPolicy = api_test_policy_defaults();
$noBackPolicy['allow_back'] = 0;
$noBackRequestJson = json_encode([
    '_test_policy' => [
        'version' => 1,
        'subject_version_id' => 12,
        'unit_id' => null,
        'settings' => $noBackPolicy,
        'hash' => 'no-back-fixture',
    ],
], JSON_THROW_ON_ERROR);
$stmt->execute([
    'activity-session-answer-002',
    42,
    12,
    null,
    null,
    'choose_test',
    'practice',
    'subject',
    null,
    'created',
    str_repeat('c', 64),
    str_repeat('d', 64),
    $noBackRequestJson,
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
$noBackPackage = api_question_session_package($pdo, ['user_id' => 42], 'activity-session-answer-002');
$noBackQuestion = $noBackPackage['questions'][0];
$noBackOptions = $noBackQuestion['payload']['options'];
$noBackCorrect = array_values(array_filter(
    $noBackOptions,
    static fn(array $option): bool => $option['text'] === '4',
))[0]['id'];
$noBackWrong = array_values(array_filter(
    $noBackOptions,
    static fn(array $option): bool => $option['text'] !== '4',
))[0]['id'];
$noBackBase = [
    'session_id' => 'activity-session-answer-002',
    'question_id' => $noBackQuestion['id'],
    'answer' => ['kind' => 'choice', 'option_id' => $noBackCorrect],
];
api_question_answer_submit(
    $pdo,
    ['user_id' => 42],
    $noBackBase,
    'question-answer-key-00000005',
);
try {
    api_question_answer_submit(
        $pdo,
        ['user_id' => 42],
        array_replace_recursive($noBackBase, [
            'answer' => ['kind' => 'choice', 'option_id' => $noBackWrong],
        ]),
        'question-answer-key-00000006',
    );
    throw new RuntimeException('Expected revision to fail when allow_back is disabled.');
} catch (QuestionAnswerTestError $error) {
    answer_check(
        $error->apiCode === 'question_already_answered' && $error->status === 409,
        'allow_back=false did not block answer revision.',
    );
}

try {
    api_question_answer_submit($pdo, ['user_id' => 43], $payload, 'question-answer-key-00000004');
    throw new RuntimeException('Expected cross-account answer to fail.');
} catch (QuestionAnswerTestError $error) {
    answer_check(
        $error->apiCode === 'activity_session_not_found' && $error->status === 404,
        'Cross-account session ownership leaked.',
    );
}

$refreshed = api_question_session_package($pdo, ['user_id' => 42], 'activity-session-answer-001');
answer_check(
    $refreshed['progress']['current_index'] === 1,
    'Confirmed server answer was not reflected in package progress.',
);

echo "Question answer idempotency tests passed.\n";
