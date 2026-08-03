<?php
declare(strict_types=1);

$apiRoot = dirname(__DIR__, 3);
require_once $apiRoot . '/_tokens.php';
require_once $apiRoot . '/_student_training_center.php';

api_require_method('GET');

$rawSubjectVersionId = trim((string)($_GET['subject_version_id'] ?? ''));
if ($rawSubjectVersionId === '' || !ctype_digit($rawSubjectVersionId)) {
    api_error('invalid_subject', 'معرف المادة غير صالح.', 422);
}
$subjectVersionId = (int)$rawSubjectVersionId;
if ($subjectVersionId <= 0) {
    api_error('invalid_subject', 'معرف المادة غير صالح.', 422);
}

$pdo = api_db();
$session = api_authenticate_access_token($pdo, api_bearer_token());
api_rate_limit('student_training_center', (string)($session['user_id'] ?? '0'), 90, 60);

api_response(true, api_student_training_center_payload($pdo, $session, $subjectVersionId));
