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
        'steps' => [
            [
                'id' => 91,
                'subject_version_id' => 12,
                'unit_id' => 4,
                'action_key' => 'learn',
                'progress_state' => 'pending',
            ],
            [
                'id' => 92,
                'subject_version_id' => 12,
                'unit_id' => 4,
                'action_key' => 'review_mistakes',
                'progress_state' => 'pending',
            ],
            [
                'id' => 93,
                'subject_version_id' => 12,
                'unit_id' => 4,
                'action_key' => 'unit_test',
                'progress_state' => 'pending',
            ],
        ],
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
require dirname(__DIR__) . '/public_html/api/_activity_preparation_guard.php';

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
$preview = api_activity_apply_authoritative_policy(
    $pdo,
    42,
    api_activity_preview($pdo, $session, $payload),
);
activity_check($preview['eligibility']['available'] === true, 'Eligible guide activity was rejected.');
activity_check((int)$preview['balances']['hearts'] === 3, 'Trusted hearts were not loaded.');
activity_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn() === $countBefore,
    'Read-only preview mutated activity sessions.',
);

$reviewPayload = array_replace($payload, [
    'activity_type' => 'review',
    'activity_mode' => 'review',
    'guide_step_id' => 92,
]);
$reviewPreview = api_activity_apply_authoritative_policy(
    $pdo,
    42,
    api_activity_preview($pdo, $session, $reviewPayload),
);
activity_check(
    $reviewPreview['eligibility']['available'] === true,
    'Server-owned review guide action was not accepted.',
);

$unitTestPayload = array_replace($payload, [
    'activity_type' => 'unit_test',
    'activity_mode' => 'test',
    'guide_step_id' => 93,
]);
$unitTestPreview = api_activity_apply_authoritative_policy(
    $pdo,
    42,
    api_activity_preview($pdo, $session, $unitTestPayload),
);
activity_check(
    $unitTestPreview['eligibility']['available'] === false
        && $unitTestPreview['eligibility']['reason_code'] === 'attempt_engine_pending',
    'Unit test was presented as startable without authoritative attempts.',
);
try {
    api_activity_start_guarded($pdo, $session, $unitTestPayload, 'phase08-unverified-00000001');
    throw new RuntimeException('Expected unit-test pending rejection was not returned.');
} catch (ActivityApiTestError $error) {
    activity_check(
        $error->apiCode === 'attempt_engine_pending' && $error->status === 409,
        'Wrong unit-test pending rejection.',
    );
}

$key = 'phase08-test-key-0000000001';
$first = api_activity_start_guarded($pdo, $session, $payload, $key);
$second = api_activity_start_guarded($pdo, $session, $payload, $key);
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

$differentKey = api_activity_start_guarded(
    $pdo,
    $session,
    $payload,
    'phase08-second-key-00000001',
);
activity_check(
    $differentKey['session_id'] === $first['session_id']
        && (int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn() === 1,
    'A second idempotency key created another active session.',
);

try {
    api_activity_start_guarded($pdo, $session, $reviewPayload, $key);
    throw new RuntimeException('Expected idempotency conflict was not returned.');
} catch (ActivityApiTestError $error) {
    activity_check(
        $error->apiCode === 'idempotency_key_conflict' && $error->status === 409,
        'Wrong idempotency conflict response.',
    );
}

$pdo->exec('UPDATE student_subject_state SET hearts=0 WHERE student_id=42 AND subject_version_id=12');
$blocked = api_activity_apply_authoritative_policy(
    $pdo,
    42,
    api_activity_preview($pdo, $session, $reviewPayload),
);
activity_check($blocked['eligibility']['available'] === false, 'Review with zero hearts was allowed.');
activity_check(
    $blocked['eligibility']['reason_code'] === 'insufficient_hearts',
    'A real zero-hearts reason was replaced by the guide policy state.',
);

// Verify that an insertion failure rolls back the outer guarded transaction completely.
$pdo->exec('UPDATE student_subject_state SET hearts=3 WHERE student_id=42 AND subject_version_id=12');
$pdo->exec('DELETE FROM api_activity_sessions');
$pdo->exec("CREATE TRIGGER fail_activity_insert BEFORE INSERT ON api_activity_sessions BEGIN SELECT RAISE(ABORT, 'forced insert failure'); END");
try {
    api_activity_start_guarded($pdo, $session, $payload, 'phase08-rollback-key-000001');
    throw new RuntimeException('Expected forced insert failure was not raised.');
} catch (PDOException) {
    activity_check(!$pdo->inTransaction(), 'Failed activity start left a transaction open.');
    activity_check(
        (int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn() === 0,
        'Failed activity start left a partial session row.',
    );
}

try {
    api_activity_preview(
        $pdo,
        $session,
        array_replace($payload, ['guide_step_id' => 999]),
    );
    throw new RuntimeException('Expected invalid guide-step rejection was not returned.');
} catch (ActivityApiTestError $error) {
    activity_check(
        $error->apiCode === 'invalid_guide_step' && $error->status === 409,
        'Wrong invalid guide-step response.',
    );
}

echo "Activity preparation behavioral tests passed.\n";
