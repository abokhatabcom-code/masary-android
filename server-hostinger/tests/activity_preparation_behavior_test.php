<?php
declare(strict_types=1);

final class ActivityApiTestError extends RuntimeException
{
    public function __construct(
        public string $apiCode,
        public int $status,
        string $message,
    ) {
        parent::__construct($message);
    }
}

function api_error(string $code, string $message, int $status = 400): never
{
    throw new ActivityApiTestError($code, $status, $message);
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

function api_server_secret(): string
{
    return str_repeat('activity-test-secret-', 3);
}

function api_student_home_smart_guide(PDO $pdo, int $studentId): array
{
    return [
        'steps' => [[
            'id' => 91,
            'subject_version_id' => 12,
            'unit_id' => 4,
            'progress_state' => 'pending',
        ]],
    ];
}

function ik_dash_profile(PDO $pdo, int $studentId): array
{
    return ['gems' => 10];
}

function ik_dash_subscription(PDO $pdo, int $studentId): array
{
    return ['status' => 'نشط', 'ends_at' => '2027-01-01'];
}

require dirname(__DIR__) . '/public_html/api/_activity_preparation.php';

function activity_check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$pdo = new PDO('sqlite::memory:');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$pdo->exec('CREATE TABLE subjects(id INTEGER PRIMARY KEY,name TEXT NOT NULL)');
$pdo->exec('CREATE TABLE subject_versions(id INTEGER PRIMARY KEY,subject_id INTEGER NOT NULL)');
$pdo->exec('CREATE TABLE units(id INTEGER PRIMARY KEY,subject_version_id INTEGER NOT NULL,title TEXT NOT NULL)');
$pdo->exec('CREATE TABLE lessons(id INTEGER PRIMARY KEY,unit_id INTEGER NOT NULL,title TEXT NOT NULL)');
$pdo->exec('CREATE TABLE student_subject_state(student_id INTEGER NOT NULL,subject_version_id INTEGER NOT NULL,hearts INTEGER NOT NULL)');
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
    heart_debited INTEGER NOT NULL,
    gems_debited INTEGER NOT NULL,
    expires_at TEXT NOT NULL,
    started_at TEXT,
    completed_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(user_id,idempotency_key_hash)
)");
$pdo->exec("INSERT INTO subjects VALUES(2,'الرياضيات')");
$pdo->exec('INSERT INTO subject_versions VALUES(12,2)');
$pdo->exec("INSERT INTO units VALUES(4,12,'الوحدة الأولى')");
$pdo->exec('INSERT INTO student_subject_state VALUES(42,12,3)');

$session = ['user_id' => 42];
$payload = [
    'subject_version_id' => 12,
    'unit_id' => 4,
    'activity_type' => 'guide_step',
    'activity_mode' => 'learn',
    'guide_step_id' => 91,
    'source' => 'home_guide',
];

$countBefore = (int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn();
$preview = api_activity_preview($pdo, $session, $payload);
activity_check($preview['eligibility']['available'] === true, 'Eligible activity was rejected.');
activity_check((int)$preview['balances']['hearts'] === 3, 'Trusted hearts were not loaded.');
activity_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn() === $countBefore,
    'Read-only preview mutated activity sessions.',
);

$key = 'phase08-test-key-0000000001';
$first = api_activity_start($pdo, $session, $payload, $key);
$second = api_activity_start($pdo, $session, $payload, $key);
activity_check($first['session_id'] === $second['session_id'], 'Idempotent replay created a second session.');
activity_check($second['replayed'] === true, 'Replayed result was not identified.');
activity_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn() === 1,
    'Duplicate activity session was stored.',
);
activity_check(
    $first['debit']['heart_debited'] === 0 && $first['debit']['gems_debited'] === 0,
    'An unapproved balance debit occurred.',
);
$status = api_activity_start_status($pdo, $session, $key);
activity_check($status['session_id'] === $first['session_id'], 'Start status returned another session.');

try {
    api_activity_start(
        $pdo,
        $session,
        array_replace($payload, ['activity_mode' => 'practice']),
        $key,
    );
    throw new RuntimeException('Expected idempotency conflict was not returned.');
} catch (ActivityApiTestError $error) {
    activity_check(
        $error->apiCode === 'idempotency_key_conflict' && $error->status === 409,
        'Wrong idempotency conflict response.',
    );
}

$pdo->exec('UPDATE student_subject_state SET hearts=0 WHERE student_id=42 AND subject_version_id=12');
$blocked = api_activity_preview(
    $pdo,
    $session,
    array_replace($payload, [
        'activity_type' => 'review',
        'activity_mode' => 'review',
        'source' => 'review',
        'guide_step_id' => null,
    ]),
);
activity_check($blocked['eligibility']['available'] === false, 'Review with zero hearts was allowed.');
activity_check(
    $blocked['eligibility']['reason_code'] === 'insufficient_hearts',
    'Missing zero-hearts rejection reason.',
);

echo "Activity preparation behavioral tests passed.\n";
