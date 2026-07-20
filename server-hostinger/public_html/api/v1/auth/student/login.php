<?php
declare(strict_types=1);

require_once dirname(__DIR__, 3) . '/_auth.php';

api_require_method('POST');
api_rate_limit('student_login', api_client_ip(), 30, 900);

$payload = api_read_json();
$username = api_string($payload, 'username', 80);
$password = (string)($payload['password'] ?? '');
$deviceName = api_device_name((string)($payload['device_name'] ?? 'Android device'));

if ($password === '' || strlen($password) > 1024) {
    api_error('invalid_credentials', 'بيانات الدخول غير صحيحة أو الحساب غير متاح حاليًا.', 401);
}

api_rate_limit('student_login_user', strtolower($username), 10, 900);

$pdo = api_db();
$student = api_find_student_for_login($pdo, $username);
if (!api_student_can_login($student, $password)) {
    api_log_auth_event($pdo, 'login_failed', null, $student ? (int)$student['id'] : null, [
        'username_hash' => hash('sha256', strtolower($username)),
    ]);
    usleep(random_int(120000, 260000));
    api_error('invalid_credentials', 'بيانات الدخول غير صحيحة أو الحساب غير متاح حاليًا.', 401);
}

$tokens = api_issue_session($pdo, $student, $deviceName);

api_response(true, [
    'access_token' => $tokens['access_token'],
    'refresh_token' => $tokens['refresh_token'],
    'expires_in' => $tokens['expires_in'],
    'student' => [
        'id' => (string)$student['id'],
        'username' => (string)$student['username'],
        'display_name' => (string)($student['full_name'] ?? ''),
    ],
]);
