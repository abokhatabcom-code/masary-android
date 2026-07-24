<?php
declare(strict_types=1);

require_once dirname(__DIR__, 2) . '/_student_home.php';
require_once dirname(__DIR__, 2) . '/_student_home_subjects.php';

api_require_method('GET');

$pdo = api_db();
$session = api_authenticate_access_token($pdo, api_bearer_token());
api_rate_limit('student_home', (string)($session['user_id'] ?? '0'), 60, 60);
$data = api_student_home_payload($pdo, $session);

$studentId = (int)($session['user_id'] ?? 0);
if ($studentId > 0) {
    $data['subjects'] = api_student_home_curriculum_subjects($pdo, $studentId);
    $data['version'] = sha1(
        (string)($data['version'] ?? '')
        . '|'
        . (json_encode($data['subjects'], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) ?: '')
    );
}

api_response(true, $data);
