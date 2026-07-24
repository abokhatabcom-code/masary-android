<?php
declare(strict_types=1);

require_once dirname(__DIR__, 3) . '/_tokens.php';
require_once dirname(__DIR__, 3) . '/_student_home.php';
require_once dirname(__DIR__, 3) . '/_student_home_subjects.php';
require_once dirname(__DIR__, 3) . '/_activity_preparation.php';
require_once dirname(__DIR__, 3) . '/_activity_preparation_guard.php';

api_require_method('POST');
$pdo = api_db();
$session = api_authenticate_access_token($pdo, api_bearer_token());
api_rate_limit('activity_start', (string)($session['user_id'] ?? '0'), 30, 60);
api_response(
    true,
    api_activity_start_guarded(
        $pdo,
        $session,
        api_read_json(),
        (string)($_SERVER['HTTP_IDEMPOTENCY_KEY'] ?? ''),
    ),
);
