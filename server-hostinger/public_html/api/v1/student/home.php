<?php
declare(strict_types=1);

require_once dirname(__DIR__, 2) . '/_student_home.php';

api_require_method('GET');
api_rate_limit('student_home', api_client_ip(), 240, 60);

$pdo = api_db();
$session = api_authenticate_access_token($pdo, api_bearer_token());
$data = api_student_home_payload($pdo, $session);

api_response(true, $data);
