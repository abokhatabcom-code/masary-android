<?php
declare(strict_types=1);

final class ProductionQuestionSchemaTestError extends RuntimeException
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
    throw new ProductionQuestionSchemaTestError($code, $status, $message);
}

function api_server_secret(): string
{
    return 'phase13-production-schema-test-secret';
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

require_once __DIR__ . '/../public_html/api/_question_answer.php';

function prod_schema_check(bool $condition, string $message): void
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
$pdo->exec("CREATE TABLE questions(
    id INTEGER PRIMARY KEY,
    subject_version_id INTEGER,
    unit_id INTEGER,
    type TEXT NOT NULL,
    question_text TEXT,
    status TEXT NOT NULL
)");
$pdo->exec("CREATE TABLE question_mcq_options(
    id INTEGER PRIMARY KEY,
    question_id INTEGER,
    option_text TEXT,
    is_correct INTEGER,
    sort_order INTEGER
)");
$pdo->exec("CREATE TABLE question_tf(
    question_id INTEGER PRIMARY KEY,
    correct_value INTEGER,
    requires_reason INTEGER
)");
$pdo->exec("CREATE TABLE question_match_pairs(
    id INTEGER PRIMARY KEY,
    question_id INTEGER,
    left_text TEXT,
    right_text TEXT,
    sort_order INTEGER
)");
$pdo->exec("CREATE TABLE question_fill(
    question_id INTEGER PRIMARY KEY,
    blanks_count INTEGER
)");
$pdo->exec("CREATE TABLE question_fill_answers(
    id INTEGER PRIMARY KEY,
    question_id INTEGER,
    blank_index INTEGER,
    answer_text TEXT
)");

$pdo->exec("INSERT INTO questions VALUES
    (101,12,7,'mcq','ما ناتج 2 + 2؟','active'),
    (102,12,7,'mcq','سؤال مؤرشف','archived'),
    (103,12,7,'tf','الشمس نجم؟','active'),
    (104,12,7,'match','صل العناصر','active'),
    (105,12,7,'fill','عاصمة مصر هي ___','active'),
    (106,12,7,'fill','سؤال فراغين','active'),
    (107,13,8,'mcq','سؤال مادة أخرى','active')
");
$pdo->exec("INSERT INTO question_mcq_options VALUES
    (1001,101,'3',0,1),
    (1002,101,'4',1,2),
    (1003,101,'5',0,3),
    (1004,102,'مؤرشف صحيح',1,1),
    (1005,102,'مؤرشف خطأ',0,2),
    (1006,107,'أ',1,1),
    (1007,107,'ب',0,2)
");
$pdo->exec("INSERT INTO question_tf VALUES (103,1,0)");
$pdo->exec("INSERT INTO question_match_pairs VALUES
    (2001,104,'مصر','القاهرة',1),
    (2002,104,'اليمن','صنعاء',2)
");
$pdo->exec("INSERT INTO question_fill VALUES (105,1),(106,2)");
$pdo->exec("INSERT INTO question_fill_answers VALUES
    (3001,105,1,'القاهرة'),
    (3002,105,1,'القاهره'),
    (3003,106,1,'أول'),
    (3004,106,2,'ثان')
");

$insertSession = $pdo->prepare(
    "INSERT INTO api_activity_sessions(
        public_session_id,user_id,subject_version_id,unit_id,lesson_id,
        activity_type,activity_mode,source,guide_step_id,status,
        idempotency_key_hash,request_hash,request_json,response_json,destination,
        heart_debited,gems_debited,expires_at,started_at,completed_at,created_at,updated_at
    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
);

function add_prod_session(PDOStatement $insert, string $id, string $activityType): void
{
    $insert->execute([
        $id,42,12,7,null,$activityType,
        $activityType === 'speed_test' ? 'speed' : 'practice',
        'subject',null,'created',
        hash('sha256', $id . ':key'),
        hash('sha256', $id . ':request'),
        '{}','{}','activity_session_pending_ui',
        0,0,'2099-01-01 00:00:00',null,null,
        '2026-09-28 00:00:00','2026-09-28 00:00:00',
    ]);
}

add_prod_session($insertSession, 'prod-choose-001', 'choose_test');
add_prod_session($insertSession, 'prod-tf-000001', 'true_false_test');
add_prod_session($insertSession, 'prod-match-001', 'connect_test');
add_prod_session($insertSession, 'prod-fill-0001', 'fill_test');
add_prod_session($insertSession, 'prod-speed-001', 'speed_test');

$choose = api_question_session_package($pdo, ['user_id' => 42], 'prod-choose-001');
prod_schema_check(count($choose['questions']) === 1, 'Production MCQ package must include one active in-scope question.');
prod_schema_check($choose['questions'][0]['type'] === 'choose', 'Production MCQ renderer type mismatch.');
prod_schema_check(count($choose['questions'][0]['payload']['options']) === 3, 'Production MCQ options were not hydrated.');
prod_schema_check(
    !str_contains(json_encode($choose, JSON_UNESCAPED_UNICODE), 'is_correct'),
    'Production MCQ package leaked grading metadata.',
);

$chooseCorrect = null;
foreach ($choose['questions'][0]['payload']['options'] as $option) {
    if ($option['text'] === '4') {
        $chooseCorrect = $option['id'];
    }
}
prod_schema_check(is_string($chooseCorrect) && $chooseCorrect !== '', 'Correct MCQ option token missing.');
$chooseResult = api_question_answer_submit($pdo, ['user_id' => 42], [
    'session_id' => 'prod-choose-001',
    'question_id' => $choose['questions'][0]['id'],
    'answer' => ['kind' => 'choice', 'option_id' => $chooseCorrect],
], 'prod-answer-key-choose-0001');
prod_schema_check($chooseResult['correct'] === true, 'Production MCQ grading failed.');

$tf = api_question_session_package($pdo, ['user_id' => 42], 'prod-tf-000001');
prod_schema_check(count($tf['questions']) === 1 && $tf['questions'][0]['type'] === 'truefalse', 'Production TF package failed.');
$trueToken = null;
foreach ($tf['questions'][0]['payload']['options'] as $option) {
    if ($option['text'] === 'صح') {
        $trueToken = $option['id'];
    }
}
$tfResult = api_question_answer_submit($pdo, ['user_id' => 42], [
    'session_id' => 'prod-tf-000001',
    'question_id' => $tf['questions'][0]['id'],
    'answer' => ['kind' => 'choice', 'option_id' => $trueToken],
], 'prod-answer-key-tf-00000001');
prod_schema_check($tfResult['correct'] === true, 'Production TF grading failed.');

$match = api_question_session_package($pdo, ['user_id' => 42], 'prod-match-001');
prod_schema_check(count($match['questions']) === 1 && $match['questions'][0]['type'] === 'connect', 'Production match package failed.');
prod_schema_check(count($match['questions'][0]['payload']['left_items']) === 2, 'Production match left side failed.');
$matchPairs = [
    [
        'left_id' => api_question_session_opaque_id('prod-match-001', 'questions', '104', 'match-left:2001'),
        'right_id' => api_question_session_opaque_id('prod-match-001', 'questions', '104', 'match-right:2001'),
    ],
    [
        'left_id' => api_question_session_opaque_id('prod-match-001', 'questions', '104', 'match-left:2002'),
        'right_id' => api_question_session_opaque_id('prod-match-001', 'questions', '104', 'match-right:2002'),
    ],
];
$matchResult = api_question_answer_submit($pdo, ['user_id' => 42], [
    'session_id' => 'prod-match-001',
    'question_id' => $match['questions'][0]['id'],
    'answer' => ['kind' => 'connections', 'pairs' => $matchPairs],
], 'prod-answer-key-match-0001');
prod_schema_check($matchResult['correct'] === true, 'Production match grading failed.');

$fill = api_question_session_package($pdo, ['user_id' => 42], 'prod-fill-0001');
prod_schema_check(count($fill['questions']) === 1, 'Only single-blank production fill questions should be exposed.');
prod_schema_check($fill['questions'][0]['type'] === 'fill', 'Production fill renderer type mismatch.');
prod_schema_check(
    !str_contains(json_encode($fill, JSON_UNESCAPED_UNICODE), 'القاهرة'),
    'Production fill package leaked accepted answer.',
);
$fillResult = api_question_answer_submit($pdo, ['user_id' => 42], [
    'session_id' => 'prod-fill-0001',
    'question_id' => $fill['questions'][0]['id'],
    'answer' => ['kind' => 'text', 'text' => 'القاهرة'],
], 'prod-answer-key-fill-00001');
prod_schema_check($fillResult['correct'] === true, 'Production fill grading failed.');

$speed = api_question_session_package($pdo, ['user_id' => 42], 'prod-speed-001');
prod_schema_check(count($speed['questions']) === 1, 'Production speed package should reuse active MCQ questions.');
prod_schema_check($speed['questions'][0]['type'] === 'speed', 'Production speed renderer type mismatch.');
$speedCorrect = null;
foreach ($speed['questions'][0]['payload']['options'] as $option) {
    if ($option['text'] === '4') {
        $speedCorrect = $option['id'];
    }
}
$speedResult = api_question_answer_submit($pdo, ['user_id' => 42], [
    'session_id' => 'prod-speed-001',
    'question_id' => $speed['questions'][0]['id'],
    'answer' => ['kind' => 'choice', 'option_id' => $speedCorrect],
], 'prod-answer-key-speed-0001');
prod_schema_check($speedResult['correct'] === true, 'Production speed grading failed.');

echo "Production question schema tests passed.\n";
