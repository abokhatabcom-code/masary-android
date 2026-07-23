<?php
declare(strict_types=1);

$source = file_get_contents(__DIR__ . '/../public_html/api/_student_home.php');
if ($source === false) {
    throw new RuntimeException('Unable to read student home source.');
}

foreach (['indicators', 'subjects', 'spotlight', 'global_rank', 'subject_version_id'] as $field) {
    if (!str_contains($source, "'{$field}'")) {
        throw new RuntimeException("Student home payload is missing {$field}.");
    }
}
if (!str_contains($source, 'WHERE ss.student_id=?')) {
    throw new RuntimeException('Subject lookup must remain scoped to the authenticated student.');
}
if (preg_match('/foreach\s*\([^)]*student/i', $source)) {
    throw new RuntimeException('Student home must not iterate over students.');
}

echo "Student home contract tests passed.\n";
