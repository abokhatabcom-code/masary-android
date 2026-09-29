<?php
declare(strict_types=1);

final class QuestionReviewPolicyTestError extends RuntimeException
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
    throw new QuestionReviewPolicyTestError($code, $status, $message);
}

function api_server_secret(): string
{
    return 'phase136-review-policy-test-secret';
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

require_once __DIR__ . '/../public_html/api/_question_result.php';

function review_check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$pdo = new PDO('sqlite::memory:');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

$pdo->exec("CREATE TABLE units(
    id INTEGER PRIMARY KEY,
    subject_version_id INTEGER NOT NULL,
    title TEXT NOT NULL
)");
$pdo->exec("CREATE TABLE questions(
    id INTEGER PRIMARY KEY,
    subject_version_id INTEGER NOT NULL,
    unit_id INTEGER NOT NULL,
    content_node_id INTEGER,
    type TEXT NOT NULL,
    difficulty TEXT NOT NULL DEFAULT 'medium',
    question_text TEXT NOT NULL,
    status TEXT NOT NULL
)");
$pdo->exec("CREATE TABLE question_mcq_options(
    id INTEGER PRIMARY KEY,
    question_id INTEGER NOT NULL,
    option_text TEXT NOT NULL,
    is_correct INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0
)");
$pdo->exec("CREATE TABLE student_unit_question_state(
    student_id INTEGER NOT NULL,
    unit_id INTEGER NOT NULL,
    question_id INTEGER NOT NULL,
    last_score REAL NOT NULL DEFAULT 0,
    last_attempt_id INTEGER,
    updated_at TEXT NOT NULL,
    PRIMARY KEY(student_id,unit_id,question_id)
)");
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

$pdo->exec("INSERT INTO units VALUES(64,25,'الوحدة')");
$pdo->exec("INSERT INTO questions VALUES
    (7001,25,64,NULL,'mcq','medium','السؤال الخاطئ','active'),
    (7002,25,64,NULL,'mcq','medium','السؤال الصحيح سابقًا','active')
");
$pdo->exec("INSERT INTO question_mcq_options VALUES
    (7101,7001,'أ',1,1),
    (7102,7001,'ب',0,2),
    (7201,7002,'أ',1,1),
    (7202,7002,'ب',0,2)
");
$pdo->exec("INSERT INTO student_unit_question_state VALUES
    (42,64,7001,0.5,10,'2026-09-29 22:00:00'),
    (42,64,7002,1.0,10,'2026-09-29 22:00:00')
");

$policy = api_test_policy_defaults();
$policy['questions_per_attempt'] = 10;
$policy['allowed_types'] = ['mcq'];
$policy['allowed_difficulties'] = ['medium'];
$policy['question_order'] = 'fixed';

function add_review_session(PDO $pdo, string $sessionId, array $policy): void
{
    $request = json_encode([
        'subject_version_id' => 25,
        'unit_id' => 64,
        'lesson_id' => null,
        'activity_type' => 'review',
        'activity_mode' => 'review',
        'source' => 'review',
        '_test_policy' => [
            'version' => 1,
            'subject_version_id' => 25,
            'unit_id' => 64,
            'settings' => $policy,
            'hash' => 'fixture',
        ],
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);

    $statement = $pdo->prepare(
        "INSERT INTO api_activity_sessions(
            public_session_id,user_id,subject_version_id,unit_id,lesson_id,
            activity_type,activity_mode,source,guide_step_id,status,
            idempotency_key_hash,request_hash,request_json,response_json,destination,
            heart_debited,gems_debited,expires_at,started_at,completed_at,created_at,updated_at
        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
    );
    $statement->execute([
        $sessionId,42,25,64,null,'review','review','review',null,'created',
        hash('sha256',$sessionId.'-key'),
        hash('sha256',$sessionId.'-request'),
        $request,'{}','activity_session_pending_ui',
        1,0,'2099-01-01 00:00:00',null,null,
        '2026-09-29 22:01:00','2026-09-29 22:01:00',
    ]);
}

add_review_session($pdo, 'review-session-0001', $policy);
$package = api_question_session_package($pdo, ['user_id' => 42], 'review-session-0001');

review_check(count($package['questions']) === 1, 'Review package must include exactly the unresolved mistake.');
review_check(
    $package['questions'][0]['prompt'] === 'السؤال الخاطئ',
    'Review package selected a fully-correct question.',
);

$correctOption = null;
foreach ($package['questions'][0]['payload']['options'] as $option) {
    if ($option['text'] === 'أ') {
        $correctOption = $option['id'];
    }
}
review_check(is_string($correctOption) && $correctOption !== '', 'Review correct option token missing.');

$answer = api_question_answer_submit(
    $pdo,
    ['user_id' => 42],
    [
        'session_id' => 'review-session-0001',
        'question_id' => $package['questions'][0]['id'],
        'answer' => ['kind' => 'choice', 'option_id' => $correctOption],
    ],
    'review-answer-key-000000001',
);
review_check(abs((float)$answer['score'] - 1.0) < 0.0001, 'Review answer did not grade from original source.');

api_question_attempt_upsert_question_state(
    $pdo,
    42,
    64,
    7001,
    1.0,
    77,
    '2026-09-29 22:03:00',
);

add_review_session($pdo, 'review-session-0002', $policy);
try {
    api_question_session_package($pdo, ['user_id' => 42], 'review-session-0002');
    throw new RuntimeException('Expected empty review after resolving the mistake.');
} catch (QuestionReviewPolicyTestError $error) {
    review_check(
        $error->apiCode === 'question_source_unavailable' && $error->status === 503,
        'Resolved mistake did not leave the review source.',
    );
}

echo "Question review policy tests passed.\n";
