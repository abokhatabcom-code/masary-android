<?php
declare(strict_types=1);

$apiRoot = dirname(__DIR__, 3);
require_once $apiRoot . '/_tokens.php';
require_once $apiRoot . '/_question_session.php';

api_require_method('GET');

$sessionId = trim((string)($_GET['session_id'] ?? ''));
if ($sessionId === '') {
    api_error('invalid_session', 'معرف جلسة النشاط غير صالح.', 422);
}

$pdo = api_db();
$session = api_authenticate_access_token($pdo, api_bearer_token());
api_rate_limit('student_question_session', (string)($session['user_id'] ?? '0'), 120, 60);

api_response(true, api_question_session_package($pdo, $session, $sessionId));
