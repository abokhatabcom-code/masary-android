<?php
declare(strict_types=1);

final class QuestionTimerPolicyTestError extends RuntimeException
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
    throw new QuestionTimerPolicyTestError($code, $status, $message);
}

function api_server_secret(): string
{
    return 'phase136-timer-policy-test-secret';
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

require_once __DIR__ . '/../public_html/api/_question_result.php';

function timer_check(bool $condition, string $message): void
{
    if (!$condition) throw new RuntimeException($message);
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
    (1,25,'السؤال الأول','أ','ب','ج','د','a',1),
    (2,25,'السؤال الثاني','أ','ب','ج','د','a',1)
");

$policy = api_test_policy_defaults();
$policy['questions_per_attempt'] = 2;
$policy['timer_seconds'] = 60;
$policy['learn_xp_max'] = 20.0;
$requestJson = json_encode([
    '_test_policy' => [
        'version' => 1,
        'subject_version_id' => 25,
        'unit_id' => null,
        'settings' => $policy,
        'hash' => 'timer-fixture',
    ],
], JSON_THROW_ON_ERROR);

$insertSession = $pdo->prepare(
    "INSERT INTO api_activity_sessions(
        public_session_id,user_id,subject_version_id,unit_id,lesson_id,
        activity_type,activity_mode,source,guide_step_id,status,
        idempotency_key_hash,request_hash,request_json,response_json,destination,
        heart_debited,gems_debited,expires_at,started_at,completed_at,created_at,updated_at
    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
);

$insertSession->execute([
    'timer-expired-session-001',42,25,null,null,
    'choose_test','learn','subject',null,'in_progress',
    str_repeat('a',64),str_repeat('b',64),$requestJson,'{}','activity_session_pending_ui',
    0,0,'2099-01-01 00:00:00',gmdate('Y-m-d H:i:s', time()-120),null,
    gmdate('Y-m-d H:i:s', time()-120),gmdate('Y-m-d H:i:s', time()-120),
]);

$package = api_question_session_package($pdo, ['user_id'=>42], 'timer-expired-session-001');
timer_check(count($package['questions']) === 2, 'Timer fixture question count mismatch.');
$correctOption = null;
foreach ($package['questions'][0]['payload']['options'] as $option) {
    if ($option['text'] === 'أ') $correctOption = $option['id'];
}
api_question_answer_submit($pdo, ['user_id'=>42], [
    'session_id'=>'timer-expired-session-001',
    'question_id'=>$package['questions'][0]['id'],
    'answer'=>['kind'=>'choice','option_id'=>$correctOption],
], 'timer-answer-key-000000001');

$result = api_question_result_finish(
    $pdo,
    ['user_id'=>42],
    ['session_id'=>'timer-expired-session-001'],
    'timer-finish-key-000000001',
);
timer_check(($result['result']['timed_out'] ?? false) === true, 'Expired timer was not reported.');
timer_check($result['result']['answered_questions'] === 1, 'Answered count must stay one.');
timer_check($result['result']['total_questions'] === 2, 'Timed finish denominator must include unanswered question.');
timer_check($result['result']['wrong_answers'] === 1, 'Unanswered timed question must count as wrong.');
timer_check($result['result']['score_percent'] === 50, 'One correct of two must score 50 percent.');
timer_check(abs((float)$result['result']['xp_earned'] - 10.0) < 0.0001, 'Timed result XP must use full denominator.');

$earlyPolicy = $policy;
$earlyRequestJson = json_encode([
    '_test_policy' => [
        'version'=>1,
        'subject_version_id'=>25,
        'unit_id'=>null,
        'settings'=>$earlyPolicy,
        'hash'=>'timer-early-fixture',
    ],
], JSON_THROW_ON_ERROR);
$insertSession->execute([
    'timer-active-session-0002',42,25,null,null,
    'choose_test','learn','subject',null,'in_progress',
    str_repeat('c',64),str_repeat('d',64),$earlyRequestJson,'{}','activity_session_pending_ui',
    0,0,'2099-01-01 00:00:00',gmdate('Y-m-d H:i:s'),null,
    gmdate('Y-m-d H:i:s'),gmdate('Y-m-d H:i:s'),
]);
$earlyPackage = api_question_session_package($pdo, ['user_id'=>42], 'timer-active-session-0002');
$earlyCorrect = null;
foreach ($earlyPackage['questions'][0]['payload']['options'] as $option) {
    if ($option['text'] === 'أ') $earlyCorrect = $option['id'];
}
api_question_answer_submit($pdo, ['user_id'=>42], [
    'session_id'=>'timer-active-session-0002',
    'question_id'=>$earlyPackage['questions'][0]['id'],
    'answer'=>['kind'=>'choice','option_id'=>$earlyCorrect],
], 'timer-answer-key-000000002');

try {
    api_question_result_finish(
        $pdo,
        ['user_id'=>42],
        ['session_id'=>'timer-active-session-0002'],
        'timer-finish-key-000000002',
    );
    throw new RuntimeException('Expected early timed finish to fail.');
} catch (QuestionTimerPolicyTestError $error) {
    timer_check(
        $error->apiCode === 'session_incomplete' && $error->status === 409,
        'Timer allowed incomplete finish before server deadline.',
    );
}

echo "Question timer policy tests passed.\n";
