<?php
declare(strict_types=1);

$payloadSource = file_get_contents(__DIR__ . '/../public_html/api/_student_subject.php');
$endpointSource = file_get_contents(__DIR__ . '/../public_html/api/v1/student/subject.php');
if ($payloadSource === false || $endpointSource === false) {
    throw new RuntimeException('Unable to read subject detail sources.');
}

foreach ([
    'api_student_subject_authorized_row',
    'api_student_home_curriculum_subjects($pdo, $studentId, 100)',
    'api_student_subject_payload',
    'student_subject_state',
    "'subject_xp'",
    "'hearts'",
    'api_student_subject_parts',
    'student_last_activity',
    "'details_available' => false",
] as $required) {
    if (!str_contains($payloadSource, $required)) {
        throw new RuntimeException("Subject detail payload is missing: {$required}");
    }
}

foreach ([
    'api_authenticate_access_token',
    "api_rate_limit('student_subject'",
    'api_student_subject_payload($pdo, $session, $subjectVersionId)',
    "$_GET['subject_version_id']",
] as $required) {
    if (!str_contains($endpointSource, $required)) {
        throw new RuntimeException("Subject detail endpoint is missing: {$required}");
    }
}

foreach (['user_id', 'student_id', 'grade_id', 'curriculum_id', 'city_id'] as $forbiddenKey) {
    if (str_contains($endpointSource, "$_GET['{$forbiddenKey}']") || str_contains($endpointSource, "$_POST['{$forbiddenKey}']")) {
        throw new RuntimeException("Subject detail endpoint must not trust external scope: {$forbiddenKey}");
    }
}

if (!str_contains($payloadSource, "api_error('subject_not_found'")) {
    throw new RuntimeException('Unauthorized and missing subjects must use the same not-found response.');
}
if (str_contains($payloadSource, 'FROM questions') || str_contains($payloadSource, 'content_html')) {
    throw new RuntimeException('Subject detail summary must not load questions or lesson HTML.');
}
if (str_contains($payloadSource, "'percent' => 0") || str_contains($payloadSource, "'value' => 1")) {
    throw new RuntimeException('Unavailable progress or level must not be invented.');
}
if (substr_count($payloadSource, 'FROM student_last_activity') !== 1) {
    throw new RuntimeException('Subject detail must load last activity once.');
}
if (str_contains($payloadSource, 'INSERT INTO') || str_contains($payloadSource, 'UPDATE ') || str_contains($payloadSource, 'DELETE FROM')) {
    throw new RuntimeException('Opening the subject page must remain read-only.');
}

echo "Student subject detail contract tests passed.\n";
