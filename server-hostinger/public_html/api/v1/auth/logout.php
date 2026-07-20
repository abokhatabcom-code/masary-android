<?php
declare(strict_types=1);

require_once dirname(__DIR__, 2) . '/_tokens.php';

api_require_method('POST');

$pdo = api_db();
$accessToken = api_bearer_token();
$payload = api_read_json();
$refreshToken = api_string($payload, 'refresh_token', 256, false);

$sessionId = null;
$userId = null;
if ($accessToken !== '') {
    $session = api_authenticate_access_token($pdo, $accessToken);
    $sessionId = (int)$session['id'];
    $userId = (int)$session['user_id'];
}

if ($sessionId === null && $refreshToken === '') {
    api_error('unauthorized', 'لا توجد جلسة صالحة لتسجيل الخروج.', 401);
}

api_revoke_session($pdo, $sessionId, $refreshToken);
api_log_auth_event($pdo, 'logout', $sessionId, $userId);

api_response(true, ['logged_out' => true]);
