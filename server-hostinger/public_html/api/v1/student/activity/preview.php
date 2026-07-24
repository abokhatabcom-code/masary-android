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
api_rate_limit('activity_preview', (string)($session['user_id'] ?? '0'), 90, 60);

$preview = api_activity_preview($pdo, $session, api_read_json());
$preview = api_activity_apply_authoritative_policy($preview);
api_response(true, api_activity_public_preview($preview));
