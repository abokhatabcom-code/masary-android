<?php
declare(strict_types=1);

$selectionSource = file_get_contents(__DIR__ . '/../public_html/api/_student_home_subjects.php');
$payloadSource = file_get_contents(__DIR__ . '/../public_html/api/_student_subjects.php');
$endpointSource = file_get_contents(__DIR__ . '/../public_html/api/v1/student/subjects.php');
if ($selectionSource === false || $payloadSource === false || $endpointSource === false) {
    throw new RuntimeException('Unable to read student subjects sources.');
}

foreach ([
    'api_student_home_curriculum_subjects',
    'api_student_home_effective_subjects',
    'api_student_home_subject_limit',
    'api_student_home_student_scope',
    'api_student_home_subject_hearts',
    'curriculum_effective_subjects',
    "'school_id'",
    "'grade_id'",
    "'city_id'",
] as $required) {
    if (!str_contains($selectionSource, $required)) {
        throw new RuntimeException("Shared subject selector is missing: {$required}");
    }
}

foreach ([
    'api_student_subjects_payload',
    'api_student_home_curriculum_subjects($pdo, $studentId, 100)',
    'curriculum_resolve_profile_id',
    'curriculum_profile_label',
    "'progress'",
    "'media'",
    "'access'",
    "'last_activity'",
    "'student_id'",
] as $required) {
    if (!str_contains($payloadSource, $required)) {
        throw new RuntimeException("Student subjects payload is missing: {$required}");
    }
}

foreach ([
    'api_authenticate_access_token',
    "api_rate_limit('student_subjects'",
    'api_student_subjects_payload($pdo, $session)',
] as $required) {
    if (!str_contains($endpointSource, $required)) {
        throw new RuntimeException("Student subjects endpoint is missing: {$required}");
    }
}

if (str_contains($endpointSource, '$_GET') || str_contains($endpointSource, '$_POST')) {
    throw new RuntimeException('Student subjects endpoint must derive scope only from the Bearer session.');
}
if (str_contains($payloadSource, "'percent' => 0")) {
    throw new RuntimeException('Unavailable subject progress must not be presented as zero percent.');
}
if (substr_count($payloadSource, 'FROM student_last_activity') !== 1) {
    throw new RuntimeException('Last activity must be loaded with one grouped read, not one query per subject.');
}
if (substr_count($selectionSource, 'FROM student_subject_state') !== 2) {
    throw new RuntimeException('Subject hearts and the legacy fallback must remain grouped reads.');
}

$loopPosition = strpos($payloadSource, 'foreach ($rawSubjects as $row)');
$queryPosition = strpos($payloadSource, 'FROM student_last_activity');
if ($loopPosition === false || $queryPosition === false || $queryPosition > $loopPosition) {
    throw new RuntimeException('Last activity query must execute before mapping the subject list.');
}

$resolverPosition = strpos($selectionSource, 'curriculum_effective_subjects(');
$legacyPosition = strpos($selectionSource, 'api_student_home_legacy_subjects($pdo, $studentId, $limit)');
if ($resolverPosition === false || $legacyPosition === false || $resolverPosition > $legacyPosition) {
    throw new RuntimeException('The official curriculum resolver must be preferred over legacy state.');
}

echo "Student subjects contract tests passed.\n";
