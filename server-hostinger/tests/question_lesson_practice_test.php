<?php
declare(strict_types=1);

final class LessonPracticeTestError extends RuntimeException
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
    throw new LessonPracticeTestError($code, $status, $message);
}

function api_server_secret(): string
{
    return 'phase13-lesson-practice-test-secret';
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

require_once __DIR__ . '/../public_html/api/_question_answer.php';

function lesson_check(bool $condition, string $message): void
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
    question_text TEXT,
    status TEXT NOT NULL
)");
$pdo->exec("CREATE TABLE version_test_settings(
    subject_version_id INTEGER PRIMARY KEY,
    questions_per_attempt INTEGER NOT NULL DEFAULT 10,
    allowed_types_json TEXT NOT NULL DEFAULT '[]',
    allowed_difficulties_json TEXT NOT NULL DEFAULT '[]',
    question_order TEXT NOT NULL DEFAULT 'random',
    shuffle_mcq_options INTEGER NOT NULL DEFAULT 1,
    shuffle_match_right INTEGER NOT NULL DEFAULT 1
)");
$pdo->exec("CREATE TABLE unit_test_settings(
    unit_id INTEGER PRIMARY KEY,
    questions_per_attempt INTEGER NOT NULL DEFAULT 10,
    allowed_types_json TEXT NOT NULL DEFAULT '[]',
    allowed_difficulties_json TEXT NOT NULL DEFAULT '[]',
    question_order TEXT NOT NULL DEFAULT 'random',
    shuffle_mcq_options INTEGER NOT NULL DEFAULT 1,
    shuffle_match_right INTEGER NOT NULL DEFAULT 1
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

$pdo->exec("INSERT INTO lessons VALUES
    (303,25,64,368,1),
    (304,25,64,369,1)
");
$pdo->exec("INSERT INTO questions VALUES
    (3451,25,64,368,'mcq','medium','أي مشكلة إيمانية يشير إليها الدرس؟','active'),
    (3466,25,64,368,'tf','easy','تعظيم الله يؤثر في السلوك.','active'),
    (3482,25,64,368,'fill','hard','من آثار الإيمان بعظمة الله الخوف من الله و____ منه.','active'),
    (3485,25,64,368,'match','medium','صِل وسيلة ترسيخ عظمة الله بالنتيجة المرتبطة بها.','active'),
    (3999,25,64,369,'mcq','medium','سؤال من درس آخر في نفس الوحدة','active')
");
$pdo->exec("INSERT INTO version_test_settings(
    subject_version_id,questions_per_attempt,allowed_types_json,allowed_difficulties_json,
    question_order,shuffle_mcq_options,shuffle_match_right
) VALUES(
    25,10,'[\"tf\",\"mcq\",\"fill\",\"match\"]','[\"easy\",\"medium\",\"hard\"]',
    'fixed',0,0
)");
$pdo->exec("INSERT INTO question_mcq_options VALUES
    (1001,3451,'ضعف الحفظ',0,1),
    (1002,3451,'عدم تعظيم الله بالشكل المطلوب',1,2),
    (1003,3451,'قلة التجارة',0,3),
    (1091,3999,'خيار أ',1,1),
    (1092,3999,'خيار ب',0,2)
");
$pdo->exec("INSERT INTO question_tf VALUES (3466,1,0)");
$pdo->exec("INSERT INTO question_fill VALUES (3482,1)");
$pdo->exec("INSERT INTO question_fill_answers VALUES
    (3001,3482,1,'الحياء')
");
$pdo->exec("INSERT INTO question_match_pairs VALUES
    (2001,3485,'تدبر القرآن الكريم','تعزيز الشعور بعظمة الله',1),
    (2002,3485,'التفكر في المخلوقات','رسوخ الإيمان بقدرة الله',2)
");

$pdo->exec("INSERT INTO api_activity_sessions(
    public_session_id,user_id,subject_version_id,unit_id,lesson_id,
    activity_type,activity_mode,source,guide_step_id,status,
    idempotency_key_hash,request_hash,request_json,response_json,destination,
    heart_debited,gems_debited,expires_at,started_at,completed_at,created_at,updated_at
) VALUES(
    'lesson-session-0001',42,25,64,303,
    'lesson_practice','learn','lesson',NULL,'created',
    'keyhash','requesthash','{}','{}','activity_session_pending_ui',
    0,0,'2099-01-01 00:00:00',NULL,NULL,'2026-09-28 00:00:00','2026-09-28 00:00:00'
)");

$package = api_question_session_package($pdo, ['user_id' => 42], 'lesson-session-0001');
lesson_check(count($package['questions']) === 4, 'Lesson practice must expose the four supported questions for the selected lesson.');
lesson_check(
    array_values(array_unique(array_column($package['questions'], 'type'))) === ['choose','truefalse','fill','connect'],
    'Lesson practice must preserve a deterministic mixed type order.',
);
lesson_check(
    !str_contains(json_encode($package, JSON_UNESCAPED_UNICODE), 'سؤال من درس آخر'),
    'Lesson practice leaked a question from another lesson in the same unit.',
);

$byType = [];
foreach ($package['questions'] as $question) {
    $byType[$question['type']] = $question;
}

$mcqCorrect = null;
foreach ($byType['choose']['payload']['options'] as $option) {
    if ($option['text'] === 'عدم تعظيم الله بالشكل المطلوب') {
        $mcqCorrect = $option['id'];
    }
}
lesson_check(is_string($mcqCorrect) && $mcqCorrect !== '', 'Lesson MCQ correct option token missing.');
$mcqResult = api_question_answer_submit($pdo, ['user_id' => 42], [
    'session_id' => 'lesson-session-0001',
    'question_id' => $byType['choose']['id'],
    'answer' => ['kind' => 'choice', 'option_id' => $mcqCorrect],
], 'lesson-answer-mcq-00000001');
lesson_check($mcqResult['correct'] === true, 'Lesson mixed MCQ grading failed.');

$trueToken = null;
foreach ($byType['truefalse']['payload']['options'] as $option) {
    if ($option['text'] === 'صح') {
        $trueToken = $option['id'];
    }
}
$tfResult = api_question_answer_submit($pdo, ['user_id' => 42], [
    'session_id' => 'lesson-session-0001',
    'question_id' => $byType['truefalse']['id'],
    'answer' => ['kind' => 'choice', 'option_id' => $trueToken],
], 'lesson-answer-tf-0000000001');
lesson_check($tfResult['correct'] === true, 'Lesson mixed TF grading failed.');

$fillResult = api_question_answer_submit($pdo, ['user_id' => 42], [
    'session_id' => 'lesson-session-0001',
    'question_id' => $byType['fill']['id'],
    'answer' => ['kind' => 'text', 'text' => 'الحياء'],
], 'lesson-answer-fill-00000001');
lesson_check($fillResult['correct'] === true, 'Lesson mixed fill grading failed.');

$matchResult = api_question_answer_submit($pdo, ['user_id' => 42], [
    'session_id' => 'lesson-session-0001',
    'question_id' => $byType['connect']['id'],
    'answer' => [
        'kind' => 'connections',
        'pairs' => [
            [
                'left_id' => api_question_session_opaque_id('lesson-session-0001', 'questions', '3485', 'match-left:2001'),
                'right_id' => api_question_session_opaque_id('lesson-session-0001', 'questions', '3485', 'match-right:2001'),
            ],
            [
                'left_id' => api_question_session_opaque_id('lesson-session-0001', 'questions', '3485', 'match-left:2002'),
                'right_id' => api_question_session_opaque_id('lesson-session-0001', 'questions', '3485', 'match-right:2002'),
            ],
        ],
    ],
], 'lesson-answer-match-0000001');
lesson_check($matchResult['correct'] === true, 'Lesson mixed match grading failed.');

// Unit override from /admin/tests/ must become authoritative over the version defaults.
$pdo->exec("INSERT INTO unit_test_settings(
    unit_id,questions_per_attempt,allowed_types_json,allowed_difficulties_json,
    question_order,shuffle_mcq_options,shuffle_match_right
) VALUES(
    64,1,'[\"tf\",\"mcq\"]','[\"easy\"]','fixed',1,1
)");
$pdo->exec("INSERT INTO api_activity_sessions(
    public_session_id,user_id,subject_version_id,unit_id,lesson_id,
    activity_type,activity_mode,source,guide_step_id,status,
    idempotency_key_hash,request_hash,request_json,response_json,destination,
    heart_debited,gems_debited,expires_at,started_at,completed_at,created_at,updated_at
) VALUES(
    'lesson-settings-0002',42,25,64,303,
    'lesson_practice','learn','lesson',NULL,'created',
    'keyhash2','requesthash2','{}','{}','activity_session_pending_ui',
    0,0,'2099-01-01 00:00:00',NULL,NULL,'2026-09-28 00:00:00','2026-09-28 00:00:00'
)");
$filtered = api_question_session_package($pdo, ['user_id' => 42], 'lesson-settings-0002');
lesson_check(
    count($filtered['questions']) === 1,
    'Admin questions_per_attempt must limit lesson practice.',
);
lesson_check(
    $filtered['questions'][0]['type'] === 'truefalse',
    'Admin type and difficulty filters must control lesson practice.',
);

echo "Lesson practice question tests passed.\n";
