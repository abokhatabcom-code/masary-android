<?php
declare(strict_types=1);

$apiRoot = dirname(__DIR__, 3);
require_once $apiRoot . '/_tokens.php';
require_once $apiRoot . '/_question_answer.php';

api_require_method('POST');

$pdo = api_db();
$session = api_authenticate_access_token($pdo, api_bearer_token());
api_rate_limit('student_question_answer', (string)($session['user_id'] ?? '0'), 180, 60);

api_response(
    true,
    api_question_answer_submit(
        $pdo,
        $session,
        api_read_json(),
        (string)($_SERVER['HTTP_IDEMPOTENCY_KEY'] ?? ''),
    ),
);
