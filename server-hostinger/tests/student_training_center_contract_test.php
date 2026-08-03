<?php
declare(strict_types=1);

$payloadSource = file_get_contents(__DIR__ . '/../public_html/api/_student_training_center.php');
$endpointSource = file_get_contents(__DIR__ . '/../public_html/api/v1/student/subject/training-center.php');
$activitySource = file_get_contents(__DIR__ . '/../public_html/api/_activity_preparation.php');
if ($payloadSource === false || $endpointSource === false || $activitySource === false) {
    throw new RuntimeException('Unable to read training center sources.');
}

foreach ([
    'api_student_subject_authorized_row',
    'api_student_training_center_payload',
    'API_TRAINING_CENTER_TOOL_DEFINITIONS',
    "'choose'",
    "'truefalse'",
    "'connect'",
    "'fill'",
    "'speed'",
    "'smart_review'",
    'api_training_center_count_questions',
    'api_training_center_count_review_errors',
    "'item_count'",
] as $required) {
    if (!str_contains($payloadSource, $required)) {
        throw new RuntimeException("Training center payload is missing: {$required}");
    }
}

foreach ([
    "require_once \$apiRoot . '/_tokens.php'",
    "require_once \$apiRoot . '/_student_training_center.php'",
    'api_authenticate_access_token',
    "api_rate_limit('student_training_center'",
    'api_student_training_center_payload($pdo, $session, $subjectVersionId)',
    "\$_GET['subject_version_id']",
] as $required) {
    if (!str_contains($endpointSource, $required)) {
        throw new RuntimeException("Training center endpoint is missing: {$required}");
    }
}

$tokensPosition = strpos($endpointSource, "require_once \$apiRoot . '/_tokens.php'");
$trainingPosition = strpos($endpointSource, "require_once \$apiRoot . '/_student_training_center.php'");
$methodPosition = strpos($endpointSource, "api_require_method('GET')");
if (
    $tokensPosition === false
    || $trainingPosition === false
    || $methodPosition === false
    || $tokensPosition > $trainingPosition
    || $trainingPosition > $methodPosition
) {
    throw new RuntimeException('Training center endpoint must bootstrap tokens before invoking API helpers.');
}

foreach (['user_id', 'student_id', 'grade_id', 'curriculum_id', 'city_id'] as $forbiddenKey) {
    if (
        str_contains($endpointSource, "\$_GET['{$forbiddenKey}']")
        || str_contains($endpointSource, "\$_POST['{$forbiddenKey}']")
    ) {
        throw new RuntimeException("Training center must not trust external scope: {$forbiddenKey}");
    }
}

if (!str_contains($payloadSource, "api_error('subject_not_found'")) {
    throw new RuntimeException('Unauthorized and missing subjects must use the not-found response.');
}
if (
    str_contains($payloadSource, 'SELECT question')
    || str_contains($payloadSource, 'SELECT answer')
    || str_contains($payloadSource, 'content_html')
) {
    throw new RuntimeException('Training center must not load question or answer content.');
}
if (
    str_contains($payloadSource, 'INSERT INTO')
    || str_contains($payloadSource, 'UPDATE ')
    || str_contains($payloadSource, 'DELETE FROM')
) {
    throw new RuntimeException('Opening the training center must remain read-only.');
}
if (substr_count($payloadSource, "'key' =>") !== 7) {
    throw new RuntimeException('Training center must keep exactly six public tool definitions.');
}

foreach ([
    "require_once __DIR__ . '/_student_training_center.php'",
    "'choose_test'",
    "'true_false_test'",
    "'connect_test'",
    "'fill_test'",
    'api_activity_training_tool',
    'api_training_center_tool_payload',
    "'training_source_unavailable'",
    "'training_empty'",
    "'question_count' => \$confirmedCount",
    "\$preview = api_activity_preview(\$pdo, \$session, \$request)",
    "if (empty(\$preview['eligibility']['available']))",
] as $required) {
    if (!str_contains($activitySource, $required)) {
        throw new RuntimeException("Activity preparation is missing training protection: {$required}");
    }
}

foreach ([
    "(string)(\$definition['activity_mode'] ?? '') === (string)\$request['activity_mode']",
    "(string)(\$definition['source'] ?? '') === (string)\$request['source']",
    "\$request['unit_id'] === null",
    "\$request['lesson_id'] === null",
    "\$request['guide_step_id'] === null",
] as $required) {
    if (!str_contains($activitySource, $required)) {
        throw new RuntimeException("Activity preparation does not validate the canonical tool contract: {$required}");
    }
}

if (str_contains($activitySource, 'SELECT question') || str_contains($activitySource, 'SELECT answer')) {
    throw new RuntimeException('Preparation must use availability counts without loading question content.');
}

echo "Student training center contract tests passed.\n";
