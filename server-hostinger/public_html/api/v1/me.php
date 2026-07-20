<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/_tokens.php';

api_require_method('GET');

$pdo = api_db();
$session = api_authenticate_access_token($pdo, api_bearer_token());

api_response(true, [
    'student' => api_student_payload($session),
    'session' => [
        'device_name' => (string)$session['device_name'],
        'access_expires_at' => (string)$session['access_expires_at'],
        'refresh_expires_at' => (string)$session['refresh_expires_at'],
    ],
]);
