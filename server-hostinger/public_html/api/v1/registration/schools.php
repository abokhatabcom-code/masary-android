<?php
declare(strict_types=1); require_once dirname(__DIR__,2).'/_registration.php'; api_require_method('GET'); $cityId=(int)($_GET['city_id']??0); if($cityId<1)api_error('city_required','المدينة مطلوبة.',422); api_response(true,['schools'=>api_registration_schools(api_db(),$cityId)]);
