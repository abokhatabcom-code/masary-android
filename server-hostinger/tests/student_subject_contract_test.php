<?php
declare(strict_types=1);

$payloadSource = file_get_contents(__DIR__ . '/../public_html/api/_student_subject.php');
$progressSource = file_get_contents(__DIR__ . '/../public_html/api/_student_subject_progress.php');
$endpointSource = file_get_contents(__DIR__ . '/../public_html/api/v1/student/subject.php');
if ($payloadSource === false || $progressSource === false || $endpointSource === false) {
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
    'api_student_subject_content_details',
    "'details_available' => (bool)\$contentDetails['details_available']",
    "'units' => \$contentDetails['units']",
    "'lessons' => \$contentDetails['lessons']",
] as $required) {
    if (!str_contains($payloadSource, $required)) {
        throw new RuntimeException("Subject detail payload is missing: {$required}");
    }
}

foreach ([
    'api_authenticate_access_token',
    "api_rate_limit('student_subject'",
    'api_student_subject_payload($pdo, $session, $subjectVersionId)',
    "\$_GET['subject_version_id']",
] as $required) {
    if (!str_contains($endpointSource, $required)) {
        throw new RuntimeException("Subject detail endpoint is missing: {$required}");
    }
}

foreach (['user_id', 'student_id', 'grade_id', 'curriculum_id', 'city_id'] as $forbiddenKey) {
    if (
        str_contains($endpointSource, "\$_GET['{$forbiddenKey}']")
        || str_contains($endpointSource, "\$_POST['{$forbiddenKey}']")
    ) {
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
if (!str_contains($payloadSource, "' ORDER BY updated_at DESC'")) {
    throw new RuntimeException('Subject detail must select the latest activity when updated_at is available.');
}
if (!str_contains($payloadSource, "' ORDER BY id DESC'")) {
    throw new RuntimeException('Subject detail must use a deterministic id fallback for legacy activity tables.');
}
if (
    str_contains($payloadSource, 'INSERT INTO')
    || str_contains($payloadSource, 'UPDATE ')
    || str_contains($payloadSource, 'DELETE FROM')
) {
    throw new RuntimeException('Opening the subject page must remain read-only.');
}
if (!str_contains($payloadSource, 'SELECT * FROM units WHERE')) {
    throw new RuntimeException('Subject content must load units in one bounded query.');
}
if (!str_contains($payloadSource, 'SELECT l.* FROM lessons l')) {
    throw new RuntimeException('Subject content must load lessons in one bounded query.');
}
foreach ([
    'unlock_threshold_percent',
    'review_progress_cap_points',
    'student_unit_state',
    'student_content_node_state',
    "'status' => 'ready'",
    "'status' => 'locked'",
    "'status' => 'completed'",
    "'status' => 'in_progress'",
] as $requiredProgressRule) {
    if (!str_contains($progressSource, $requiredProgressRule)) {
        throw new RuntimeException("Subject progress projection is missing: {$requiredProgressRule}");
    }
}
if (!str_contains($progressSource, "$unlockMode === 'free'")) {
    throw new RuntimeException('Free unlock mode must remain supported.');
}
if (!str_contains($progressSource, "$unlockMode === 'within_unit'")) {
    throw new RuntimeException('Within-unit lesson unlocking must remain supported.');
}
if (
    str_contains($progressSource, 'INSERT INTO')
    || str_contains($progressSource, 'UPDATE ')
    || str_contains($progressSource, 'DELETE FROM')
) {
    throw new RuntimeException('Projecting progress states must remain read-only.');
}
if (!str_contains($payloadSource, "'preparation' => [")) {
    throw new RuntimeException('Lessons must expose a preparation gate instead of starting implicitly.');
}
if (!str_contains($payloadSource, 'outside the published subject tree')) {
    throw new RuntimeException('Lessons outside the published unit tree must be rejected.');
}
// Subject content details must remain read-only.

echo "Student subject detail contract tests passed.\n";
