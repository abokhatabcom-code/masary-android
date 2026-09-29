<?php
declare(strict_types=1);

final class QuestionFairGradingTestError extends RuntimeException
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
    throw new QuestionFairGradingTestError($code, $status, $message);
}

function api_server_secret(): string
{
    return 'phase136-fair-grading-test-secret';
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

require_once __DIR__ . '/../public_html/api/_question_answer.php';

function fair_check(bool $condition, string $message): void
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
$pdo->exec("CREATE TABLE lessons(
    id INTEGER PRIMARY KEY,
    subject_version_id INTEGER NOT NULL,
    unit_id INTEGER NOT NULL,
    content_node_id INTEGER NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 1
)");
$pdo->exec("CREATE TABLE questions(
    id INTEGER PRIMARY KEY,
    subject_version_id INTEGER,
    unit_id INTEGER,
    content_node_id INTEGER,
    type TEXT NOT NULL,
    difficulty TEXT NOT NULL DEFAULT 'medium',
    question_text TEXT NOT NULL,
    status TEXT NOT NULL
)");
$pdo->exec("CREATE TABLE question_tf(
    question_id INTEGER PRIMARY KEY,
    correct_value INTEGER NOT NULL,
    requires_reason INTEGER NOT NULL DEFAULT 0
)");
$pdo->exec("CREATE TABLE question_tf_reasons(
    id INTEGER PRIMARY KEY,
    question_id INTEGER NOT NULL,
    label TEXT NOT NULL,
    is_correct INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0
)");
$pdo->exec("CREATE TABLE question_fill(
    question_id INTEGER PRIMARY KEY,
    blanks_count INTEGER NOT NULL
)");
$pdo->exec("CREATE TABLE question_fill_answers(
    id INTEGER PRIMARY KEY,
    question_id INTEGER NOT NULL,
    blank_index INTEGER NOT NULL,
    answer_text TEXT NOT NULL
)");
$pdo->exec("CREATE TABLE question_direct(
    question_id INTEGER PRIMARY KEY,
    answer_text TEXT NOT NULL
)");
$pdo->exec("CREATE TABLE question_match_pairs(
    id INTEGER PRIMARY KEY,
    question_id INTEGER NOT NULL,
    left_text TEXT NOT NULL,
    right_text TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0
)");
$pdo->exec("CREATE TABLE version_test_settings(
    subject_version_id INTEGER PRIMARY KEY,
    questions_per_attempt INTEGER NOT NULL,
    allowed_types_json TEXT NOT NULL,
    allowed_difficulties_json TEXT NOT NULL,
    question_order TEXT NOT NULL,
    shuffle_mcq_options INTEGER NOT NULL,
    shuffle_match_right INTEGER NOT NULL,
    allow_back INTEGER NOT NULL,
    allow_skip INTEGER NOT NULL,
    reveal_answers INTEGER NOT NULL,
    tf_reason_only_on_false INTEGER NOT NULL,
    pass_percent INTEGER NOT NULL,
    timer_seconds INTEGER NOT NULL,
    learn_xp_max REAL NOT NULL,
    review_xp_max REAL NOT NULL,
    review_heart_cost INTEGER NOT NULL,
    show_mistakes_button_unit INTEGER NOT NULL,
    show_mistakes_button_result INTEGER NOT NULL,
    show_mistakes_button_achievements INTEGER NOT NULL,
    mistakes_heart_cost INTEGER NOT NULL,
    mistakes_xp_total REAL NOT NULL,
    result_show_pass_badge INTEGER NOT NULL,
    result_show_score INTEGER NOT NULL,
    result_show_counts_correct INTEGER NOT NULL,
    result_show_counts_partial INTEGER NOT NULL,
    result_show_counts_wrong INTEGER NOT NULL,
    result_show_xp INTEGER NOT NULL,
    result_show_hearts_spent INTEGER NOT NULL,
    result_show_retry_button INTEGER NOT NULL,
    result_show_back_button INTEGER NOT NULL,
    result_show_review_details INTEGER NOT NULL,
    active_time_enabled INTEGER NOT NULL,
    active_time_idle_seconds INTEGER NOT NULL,
    active_time_ping_interval INTEGER NOT NULL
)");

$pdo->exec("INSERT INTO lessons VALUES(303,25,64,368,1)");
$pdo->exec("INSERT INTO questions VALUES
    (5001,25,64,368,'tf','medium','عبارة تحتاج سببًا','active'),
    (5002,25,64,368,'fill','medium','أكمل فراغين','active'),
    (5003,25,64,368,'direct','medium','اكتب اسم العاصمة','active'),
    (5004,25,64,368,'match','medium','صل العناصر','active')
");
$pdo->exec("INSERT INTO question_tf VALUES(5001,0,1)");
$pdo->exec("INSERT INTO question_tf_reasons VALUES
    (5101,5001,'السبب الصحيح',1,1,1),
    (5102,5001,'سبب غير صحيح',0,1,2)
");
$pdo->exec("INSERT INTO question_fill VALUES(5002,2)");
$pdo->exec("INSERT INTO question_fill_answers VALUES
    (5201,5002,1,'القاهرة'),
    (5202,5002,2,'اليمن')
");
$pdo->exec("INSERT INTO question_direct VALUES(5003,'القاهرة')");
$pdo->exec("INSERT INTO question_match_pairs VALUES
    (5301,5004,'مصر','القاهرة',1),
    (5302,5004,'اليمن','صنعاء',2)
");

$policy = api_test_policy_defaults();
$policy['questions_per_attempt'] = 4;
$policy['allowed_types'] = ['tf', 'fill', 'direct', 'match'];
$policy['allowed_difficulties'] = ['easy', 'medium', 'hard'];
$policy['question_order'] = 'fixed';
$policy['shuffle_match_right'] = 1;

$requestJson = json_encode([
    'subject_version_id' => 25,
    'unit_id' => 64,
    'lesson_id' => 303,
    'activity_type' => 'lesson_practice',
    'activity_mode' => 'learn',
    'source' => 'lesson',
    '_test_policy' => [
        'version' => 1,
        'subject_version_id' => 25,
        'unit_id' => 64,
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
    'fair-grading-session-001',
    42,25,64,303,'lesson_practice','learn','lesson',null,'created',
    str_repeat('a',64),str_repeat('b',64),$requestJson,'{}',
    'activity_session_pending_ui',0,0,'2099-01-01 00:00:00',
    null,null,'2026-09-29 00:00:00','2026-09-29 00:00:00',
]);

$package = api_question_session_package(
    $pdo,
    ['user_id' => 42],
    'fair-grading-session-001',
);
fair_check(count($package['questions']) === 4, 'Expected all four admin-enabled question types.');

$byType = [];
foreach ($package['questions'] as $question) {
    $byType[$question['type']] = $question;
}
fair_check(isset($byType['direct']), 'Direct question was not exposed.');
fair_check(
    (int)($byType['fill']['payload']['blanks_count'] ?? 0) === 2,
    'Multi-fill metadata was not exposed.',
);
fair_check(
    !empty($byType['truefalse']['payload']['requires_reason'])
    && count($byType['truefalse']['payload']['reasons'] ?? []) === 2,
    'TF reason metadata was not exposed.',
);

$falseId = null;
foreach ($byType['truefalse']['payload']['options'] as $option) {
    if ($option['text'] === 'خطأ') $falseId = $option['id'];
}
$wrongReasonId = null;
foreach ($byType['truefalse']['payload']['reasons'] as $reason) {
    if ($reason['text'] === 'سبب غير صحيح') $wrongReasonId = $reason['id'];
}
$tfResult = api_question_answer_submit(
    $pdo,
    ['user_id' => 42],
    [
        'session_id' => 'fair-grading-session-001',
        'question_id' => $byType['truefalse']['id'],
        'answer' => [
            'kind' => 'choice',
            'option_id' => $falseId,
            'reason_id' => $wrongReasonId,
        ],
    ],
    'fair-answer-tf-0000000001',
);
fair_check(abs((float)$tfResult['score'] - 0.5) < 0.0001, 'TF+reason must award half credit.');

$fillResult = api_question_answer_submit(
    $pdo,
    ['user_id' => 42],
    [
        'session_id' => 'fair-grading-session-001',
        'question_id' => $byType['fill']['id'],
        'answer' => [
            'kind' => 'fill',
            'blanks' => ['القاهرة', 'إجابة أخرى'],
        ],
    ],
    'fair-answer-fill-00000001',
);
fair_check(abs((float)$fillResult['score'] - 0.5) < 0.0001, 'One of two fill blanks must award half credit.');

$directResult = api_question_answer_submit(
    $pdo,
    ['user_id' => 42],
    [
        'session_id' => 'fair-grading-session-001',
        'question_id' => $byType['direct']['id'],
        'answer' => [
            'kind' => 'text',
            'text' => 'القاهره',
        ],
    ],
    'fair-answer-direct-0000001',
);
fair_check(abs((float)$directResult['score'] - 1.0) < 0.0001, 'Arabic tolerant direct grading failed.');

$matchLeft = $byType['connect']['payload']['left_items'];
$matchRight = $byType['connect']['payload']['right_items'];
$correctLeft = api_question_session_opaque_id(
    'fair-grading-session-001',
    'questions',
    '5004',
    'match-left:5301',
);
$correctRight = api_question_session_opaque_id(
    'fair-grading-session-001',
    'questions',
    '5004',
    'match-right:5301',
);
$matchResult = api_question_answer_submit(
    $pdo,
    ['user_id' => 42],
    [
        'session_id' => 'fair-grading-session-001',
        'question_id' => $byType['connect']['id'],
        'answer' => [
            'kind' => 'connections',
            'pairs' => [[
                'left_id' => $correctLeft,
                'right_id' => $correctRight,
            ]],
        ],
    ],
    'fair-answer-match-0000001',
);
fair_check(abs((float)$matchResult['score'] - 0.5) < 0.0001, 'One of two match pairs must award half credit.');

$score = api_question_result_score($pdo, 42, 'fair-grading-session-001');
fair_check($score['correct_answers'] === 1, 'Expected one fully correct answer.');
fair_check($score['partial_answers'] === 3, 'Expected three partial answers.');
fair_check($score['wrong_answers'] === 0, 'Expected no fully wrong answers.');
fair_check(abs((float)$score['score_percent_exact'] - 62.5) < 0.0001, 'Expected exact score of 62.5 percent.');
fair_check($score['score_percent'] === 63, 'Expected rounded score of 63 percent.');

echo "Question fair grading tests passed.\n";
