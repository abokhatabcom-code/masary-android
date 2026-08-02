<?php
declare(strict_types=1);

final class LegacyActivityApiTestError extends RuntimeException
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
    throw new LegacyActivityApiTestError($code, $status, $message);
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

function api_server_secret(): string
{
    return str_repeat('legacy-activity-test-', 4);
}

function api_student_home_smart_guide(PDO $pdo, int $studentId): array
{
    return [
        'steps' => [[
            'id' => 501,
            'subject_version_id' => 12,
            'unit_id' => 4,
            'action_key' => 'learn',
            'progress_state' => 'pending',
        ]],
    ];
}

function ik_dash_profile(PDO $pdo, int $studentId): array
{
    return ['gems' => 99];
}

function ik_dash_subscription(PDO $pdo, int $studentId): array
{
    return ['status' => 'نشط', 'ends_at' => '2027-01-01'];
}

require dirname(__DIR__) . '/public_html/api/_activity_preparation.php';
require dirname(__DIR__) . '/public_html/api/_activity_preparation_guard.php';
require dirname(__DIR__) . '/public_html/api/_activity_preparation_compat.php';

function legacy_check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

function legacy_complete_attempt(PDO $pdo, int $unitId): void
{
    $stmt = $pdo->prepare(
        'INSERT INTO attempts_unit_tests '
        . '(student_id,unit_id,test_type,attempt_number,is_completed) VALUES(42,?,?,1,1)',
    );
    foreach (['connect', 'fill', 'choose', 'truefalse', 'speed'] as $type) {
        $stmt->execute([$unitId, $type]);
    }
}

$pdo = new PDO('sqlite::memory:');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$pdo->exec('CREATE TABLE subjects(id INTEGER PRIMARY KEY,name TEXT NOT NULL)');
$pdo->exec('CREATE TABLE subject_versions(id INTEGER PRIMARY KEY,subject_id INTEGER NOT NULL)');
$pdo->exec('CREATE TABLE units(id INTEGER PRIMARY KEY,subject_id INTEGER NOT NULL,name TEXT NOT NULL)');
$pdo->exec('CREATE TABLE lessons(id INTEGER PRIMARY KEY,unit_id INTEGER NOT NULL,name TEXT NOT NULL)');
$pdo->exec('CREATE TABLE student_subject_state(student_id INTEGER NOT NULL,subject_version_id INTEGER NOT NULL,hearts INTEGER NOT NULL)');
$pdo->exec('CREATE TABLE student_subject_hearts(student_id INTEGER NOT NULL,subject_id INTEGER NOT NULL,hearts INTEGER NOT NULL,last_heart_time TEXT)');
$pdo->exec('CREATE TABLE student_gems(student_id INTEGER NOT NULL,gems INTEGER NOT NULL)');
$pdo->exec('CREATE TABLE student_unit_points(
    student_id INTEGER NOT NULL,
    unit_id INTEGER NOT NULL,
    review_points REAL NOT NULL,
    max_review_points REAL NOT NULL,
    unit_attempts INTEGER NOT NULL,
    UNIQUE(student_id,unit_id)
)');
$pdo->exec('CREATE TABLE attempts_unit_tests(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id INTEGER NOT NULL,
    unit_id INTEGER NOT NULL,
    test_type TEXT NOT NULL,
    attempt_number INTEGER NOT NULL,
    is_completed INTEGER NOT NULL
)');
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
$pdo->exec("INSERT INTO units VALUES(3,2,'الوحدة الأولى')");
$pdo->exec("INSERT INTO units VALUES(4,2,'الوحدة الثانية')");
$pdo->exec('INSERT INTO student_subject_state VALUES(42,12,3)');
$pdo->exec("INSERT INTO student_subject_hearts VALUES(42,2,1,'2026-07-24 12:00:00')");
$pdo->exec('INSERT INTO student_gems VALUES(42,7)');
$pdo->exec('INSERT INTO student_unit_points VALUES(42,3,15,50,1)');
legacy_complete_attempt($pdo, 3);

$session = ['user_id' => 42];
$payload = [
    'subject_version_id' => 12,
    'unit_id' => 4,
    'activity_type' => 'guide_step',
    'activity_mode' => 'learn',
    'guide_step_id' => 501,
    'source' => 'home_guide',
];

$preview = api_activity_preview_compatible($pdo, $session, $payload);
legacy_check($preview['eligibility']['available'] === true, 'Legacy unit was not unlocked.');
legacy_check($preview['activity']['unit_title'] === 'الوحدة الثانية', 'Legacy unit title was not read.');
legacy_check($preview['balances']['hearts'] === 1, 'Legacy subject hearts did not override the fallback state.');
legacy_check($preview['balances']['gems'] === 7, 'Legacy global gems were not read.');
legacy_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn() === 0,
    'Legacy preview wrote a session.',
);

$result = api_activity_start_compatible_guarded(
    $pdo,
    $session,
    $payload,
    'phase08-legacy-start-key-0001',
);
legacy_check($result['session_id'] !== '', 'Legacy compatible start did not create a session.');
legacy_check($result['balances']['hearts'] === 1, 'Start response lost legacy heart balance.');
legacy_check($result['balances']['gems'] === 7, 'Start response lost legacy gem balance.');
legacy_check(
    (int)$pdo->query('SELECT COUNT(*) FROM api_activity_sessions')->fetchColumn() === 1,
    'Legacy compatible start did not persist exactly one session.',
);

echo "Legacy activity preparation compatibility tests passed.\n";
