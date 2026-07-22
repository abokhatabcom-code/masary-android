<?php
declare(strict_types=1); require_once dirname(__DIR__,3).'/_registration.php'; api_require_method('POST'); api_rate_limit('student_register',api_client_ip(),10,3600); $key=api_idempotency_key(); $payload=api_read_json(); $data=api_registration_idempotency($key,static fn():array=>api_register_student(api_db(),$payload)); api_response(true,$data,null,201);
