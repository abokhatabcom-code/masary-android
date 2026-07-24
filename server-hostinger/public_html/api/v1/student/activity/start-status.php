<?php
declare(strict_types=1);

require_once dirname(__DIR__, 3) . '/_tokens.php';
require_once dirname(__DIR__, 3) . '/_activity_preparation.php';

api_require_method('GET');
$pdo = api_db();
$session = api_authenticate_access_token($pdo, api_bearer_token());
api_rate_limit('activity_start_status', (string)($session['user_id'] ?? '0'), 60, 60);
api_response(
    true,
    api_activity_start_status(
        $pdo,
        $session,
        (string)($_SERVER['HTTP_IDEMPOTENCY_KEY'] ?? ''),
    ),
);
