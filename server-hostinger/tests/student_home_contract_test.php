<?php
declare(strict_types=1);

$baseSource = file_get_contents(__DIR__ . '/../public_html/api/_student_home.php');
$subjectSource = file_get_contents(__DIR__ . '/../public_html/api/_student_home_subjects.php');
$endpointSource = file_get_contents(__DIR__ . '/../public_html/api/v1/student/home.php');
if ($baseSource === false || $subjectSource === false || $endpointSource === false) {
    throw new RuntimeException('Unable to read student home sources.');
}

foreach (['indicators', 'subjects', 'spotlight', 'global_rank', 'subject_version_id'] as $field) {
    if (!str_contains($baseSource, "'{$field}'")) {
        throw new RuntimeException("Student home payload is missing {$field}.");
    }
}

foreach (['sv.grade_id=?', 'sv.curriculum_id=?', 'LEFT JOIN student_subject_state', 'ORDER BY'] as $required) {
    if (!str_contains($subjectSource, $required)) {
        throw new RuntimeException("Curriculum subject adapter is missing: {$required}");
    }
}
if (!str_contains($endpointSource, 'api_student_home_curriculum_subjects($pdo, $studentId)')) {
    throw new RuntimeException('Home endpoint must use the curriculum-aware subject adapter.');
}
if (str_contains($endpointSource, '$_GET') || str_contains($endpointSource, '$_POST')) {
    throw new RuntimeException('Home endpoint must derive the student only from the Bearer session.');
}
if (preg_match('/foreach\s*\([^)]*student/i', $baseSource . $subjectSource)) {
    throw new RuntimeException('Student home must not iterate over students.');
}

echo "Student home contract tests passed.\n";
