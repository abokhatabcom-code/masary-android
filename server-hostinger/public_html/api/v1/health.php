<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/_database.php';

api_require_method('GET');

$pdo = api_db();
$pdo->query('SELECT 1')->fetchColumn();

api_response(true, [
    'service' => 'masary-api',
    'version' => MASARY_API_VERSION,
    'status' => 'ok',
    'server_time' => gmdate(DATE_ATOM),
]);
