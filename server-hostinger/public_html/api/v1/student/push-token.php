<?php
declare(strict_types=1);
require_once dirname(__DIR__, 2) . '/_bootstrap.php';
require_once dirname(__DIR__, 2) . '/_database.php';
require_once dirname(__DIR__, 2) . '/_tokens.php';
require_once dirname(__DIR__, 2) . '/_push_tokens.php';
api_require_https(); api_maintenance_guard(); api_require_method('PUT', 'DELETE');
$pdo = api_db();
$user = api_authenticate_access_token($pdo, api_bearer_token());
$payload = api_read_json();
if (($_SERVER['REQUEST_METHOD'] ?? '') === 'DELETE') api_delete_android_push_token($pdo, (int)$user['user_id'], $payload);
else api_upsert_android_push_token($pdo, (int)$user['user_id'], $payload);
api_response(true, ['registered' => ($_SERVER['REQUEST_METHOD'] ?? '') === 'PUT']);
