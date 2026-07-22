<?php
declare(strict_types=1); require_once dirname(__DIR__,2).'/_registration.php'; api_require_method('GET'); api_response(true,['cities'=>api_registration_cities(api_db())]);
