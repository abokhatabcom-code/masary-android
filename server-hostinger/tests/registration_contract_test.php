<?php
declare(strict_types=1);
$source=(string)file_get_contents(__DIR__.'/../public_html/api/_registration.php');
$checks=["cities WHERE is_active=1","COALESCE(student_visibility,'public')='public'","s.city_id=?","school_id']=null","GET_LOCK(?,10)","password_hash(","api_issue_session","api_registration_idempotency"];
foreach($checks as $check){if(!str_contains($source,$check)){fwrite(STDERR,"Missing registration protection: $check\n");exit(1);}}
$endpoint=(string)file_get_contents(__DIR__.'/../public_html/api/v1/auth/student/register.php');
if(!str_contains($endpoint,'api_idempotency_key()')){exit(1);} echo "Registration adapter checks passed.\n";
