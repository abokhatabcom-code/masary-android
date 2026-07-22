<?php
declare(strict_types=1);
$source=(string)file_get_contents(__DIR__.'/../public_html/api/_registration.php');
$checks=["cities WHERE is_active=1","COALESCE(student_visibility,'public')='public'","s.city_id=?","school_id']=null","GET_LOCK(?,10)","password_hash(","api_issue_session","idempotency_key_conflict","hash_hmac('sha256'",'chmod($path, 0600)',"FILTER_VALIDATE_EMAIL","['explorer', 'achiever', 'calm']"];
foreach($checks as $check){if(!str_contains($source,$check)){fwrite(STDERR,"Missing registration protection: $check\n");exit(1);}}
if (str_contains($source, "'password' => \$normalized") || str_contains($source, "'password' => \$payload")) { fwrite(STDERR,"Password persisted in idempotency record.\n"); exit(1); }
$endpoint=(string)file_get_contents(__DIR__.'/../public_html/api/v1/auth/student/register.php');
foreach(['api_idempotency_key()','api_registration_fingerprint($payload)'] as $check){if(!str_contains($endpoint,$check))exit(1);} echo "Registration adapter checks passed.\n";
