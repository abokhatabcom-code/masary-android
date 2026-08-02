<?php
declare(strict_types=1);

require_once dirname(__DIR__, 2) . '/_student_subject.php';

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
api_rate_limit('student_subject', (string)($session['user_id'] ?? '0'), 90, 60);

$data = api_student_subject_payload($pdo, $session, $subjectVersionId);
$data['actions']['training_center'] = [
    'available' => true,
    'reason' => '',
];
$data['version'] = hash(
    'sha256',
    json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
);

api_response(true, $data);
