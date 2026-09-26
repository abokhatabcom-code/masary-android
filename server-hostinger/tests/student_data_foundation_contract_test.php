<?php
declare(strict_types=1);

$homeSource = file_get_contents(__DIR__ . '/../public_html/api/_student_home.php');
$subjectSource = file_get_contents(__DIR__ . '/../public_html/api/_student_subject.php');
if ($homeSource === false || $subjectSource === false) {
    throw new RuntimeException('Unable to read phase 12.5 student data sources.');
}

foreach ([
    'api_student_home_subscription',
    'api_student_home_effective_hearts',
    "'hearts_refill_date'",
    "status='active'",
    'ends_at>=?',
    'calc_level($globalXp, (int)$levelStep)',
] as $required) {
    if (!str_contains($homeSource, $required)) {
        throw new RuntimeException("Home data foundation is missing: {$required}");
    }
}

if (str_contains($homeSource, "function_exists('ik_dash_subscription')")) {
    throw new RuntimeException('Home must not accept an expired active subscription from the legacy lightweight helper.');
}

foreach ([
    'api_student_subject_level_state',
    'calc_level($xp, 100)',
    "'progress_percent'",
    "'hearts_refill_date'",
    "new DateTimeZone('Asia/Aden')",
    'يعيد نظام المنصة القلوب يوميًا إلى 3 عند بداية يوم جديد',
] as $required) {
    if (!str_contains($subjectSource, $required)) {
        throw new RuntimeException("Subject data foundation is missing: {$required}");
    }
}

if (str_contains($subjectSource, 'قاعدة الثماني ساعات')) {
    throw new RuntimeException('The obsolete eight-hour hearts rule must not remain in the subject API.');
}

if (
    str_contains($subjectSource, 'INSERT INTO')
    || str_contains($subjectSource, 'UPDATE ')
    || str_contains($subjectSource, 'DELETE FROM')
) {
    throw new RuntimeException('Subject data projection must stay read-only.');
}

echo "student_data_foundation_contract_test: ok\n";
