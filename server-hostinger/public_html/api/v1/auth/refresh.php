<?php
declare(strict_types=1);

require_once dirname(__DIR__, 2) . '/_tokens.php';

api_require_method('POST');
api_rate_limit('refresh', api_client_ip(), 120, 900);

$payload = api_read_json();
$refreshToken = api_string($payload, 'refresh_token', 256);

$pdo = api_db();
$data = api_rotate_refresh_token($pdo, $refreshToken);

api_response(true, $data);
