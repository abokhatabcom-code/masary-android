<?php
declare(strict_types=1);

require_once __DIR__ . '/_bootstrap.php';

api_require_method('GET');
api_response(true, [
    'service' => 'masary-api',
    'version' => MASARY_API_VERSION,
    'documentation' => 'See server-hostinger/README.md in the private repository.',
]);
