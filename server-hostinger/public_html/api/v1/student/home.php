<?php
declare(strict_types=1);

require_once dirname(__DIR__, 2) . '/_student_home.php';

api_require_method('GET');

$pdo = api_db();
$session = api_authenticate_access_token($pdo, api_bearer_token());
api_rate_limit('student_home', (string)($session['user_id'] ?? '0'), 60, 60);
$data = api_student_home_payload($pdo, $session);

api_response(true, $data);
