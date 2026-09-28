<?php
declare(strict_types=1);

final class QuestionSessionTestError extends RuntimeException
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
    throw new QuestionSessionTestError($code, $status, $message);
}

function api_server_secret(): string
{
    return 'phase13-test-secret';
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

require_once __DIR__ . '/../public_html/api/_question_session.php';

function question_session_check(bool $condition, string $message): void
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

$pdo->exec("INSERT INTO choose_questions VALUES
    (1,12,'ما ناتج 2 + 2؟','1','2','4','5','SECRET_CORRECT_COLUMN_VALUE',1),
    (2,13,'سؤال مادة أخرى','أ','ب','ج','د','SECRET_OTHER',1),
    (3,12,'سؤال غير نشط','أ','ب','ج','د','SECRET_DISABLED',0)");

$insertSession = $pdo->prepare(
    "INSERT INTO api_activity_sessions(
        public_session_id,user_id,subject_version_id,unit_id,lesson_id,
        activity_type,activity_mode,source,guide_step_id,status,
        idempotency_key_hash,request_hash,request_json,response_json,destination,
        heart_debited,gems_debited,expires_at,started_at,completed_at,created_at,updated_at
    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
);
$insertSession->execute([
    'activity-session-001',
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

$countBefore = (int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn();
$package = api_question_session_package($pdo, ['user_id' => 42], 'activity-session-001');

question_session_check($package['session']['id'] === 'activity-session-001', 'Wrong session was returned.');
question_session_check($package['session']['subject_version_id'] === 12, 'Subject identity changed.');
question_session_check($package['progress']['current_index'] === 0, 'Initial progress must start at zero.');
question_session_check($package['progress']['total_questions'] === 1, 'Question scope leaked another subject/inactive row.');
question_session_check(count($package['questions']) === 1, 'Expected exactly one supported question.');

$question = $package['questions'][0];
question_session_check($question['type'] === 'choose', 'Wrong renderer type.');
question_session_check($question['prompt'] === 'ما ناتج 2 + 2؟', 'Question prompt mismatch.');
question_session_check($question['id'] !== '1' && strlen($question['id']) === 32, 'Database question id was exposed.');
question_session_check(count($question['payload']['options']) === 4, 'Choose options were not normalized.');
question_session_check(
    !str_contains(json_encode($package, JSON_UNESCAPED_UNICODE), 'SECRET_CORRECT_COLUMN_VALUE'),
    'Correct-answer column leaked into the package.',
);
question_session_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn() === $countBefore,
    'Reading a question package mutated activity sessions.',
);
question_session_check(
    (string)$pdo->query("SELECT status FROM api_activity_sessions WHERE public_session_id='activity-session-001'")->fetchColumn()
        === 'created',
    'Reading a package changed session status.',
);

try {
    api_question_session_package($pdo, ['user_id' => 43], 'activity-session-001');
    throw new RuntimeException('Expected cross-account session lookup to fail.');
} catch (QuestionSessionTestError $error) {
    question_session_check(
        $error->apiCode === 'activity_session_not_found' && $error->status === 404,
        'Cross-account lookup must be indistinguishable from a missing session.',
    );
}

$endpointSource = file_get_contents(
    __DIR__ . '/../public_html/api/v1/student/activity/session.php',
);
question_session_check($endpointSource !== false, 'Unable to read question session endpoint.');
foreach ([
    "api_require_method('GET')",
    "api_authenticate_access_token",
    "api_question_session_package",
    "\$_GET['session_id']",
    "api_rate_limit('student_question_session'",
] as $required) {
    question_session_check(
        str_contains((string)$endpointSource, $required),
        "Question session endpoint is missing: {$required}",
    );
}
foreach (['student_id', 'user_id', 'subject_version_id'] as $forbidden) {
    question_session_check(
        !str_contains((string)$endpointSource, "\$_GET['{$forbidden}']"),
        "Endpoint must not trust external scope: {$forbidden}",
    );
}

echo "Question session package tests passed.\n";
