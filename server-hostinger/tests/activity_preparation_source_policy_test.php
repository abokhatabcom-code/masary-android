<?php
declare(strict_types=1);

function api_student_home_smart_guide(PDO $pdo, int $studentId): array
{
    return [
        'steps' => [[
            'id' => 91,
            'subject_version_id' => 12,
            'unit_id' => null,
            'action_key' => 'learn',
            'progress_state' => 'pending',
        ]],
    ];
}

function api_activity_table_exists(PDO $pdo, string $table): bool
{
    return false;
}

require dirname(__DIR__) . '/public_html/api/_activity_preparation_guard.php';

function source_policy_check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

function source_policy_preview(array $request): array
{
    return [
        'eligibility' => [
            'available' => true,
            'status' => 'ready',
            'reason' => '',
            'reason_code' => '',
        ],
        'subscription' => [
            'blocking' => false,
            'active' => true,
        ],
        '_normalized_request' => $request,
    ];
}

$pdo = new PDO('sqlite::memory:');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

$subjectTraining = api_activity_apply_authoritative_policy(
    $pdo,
    42,
    source_policy_preview([
        'subject_version_id' => 12,
        'unit_id' => null,
        'lesson_id' => null,
        'activity_type' => 'choose_test',
        'activity_mode' => 'practice',
        'guide_step_id' => null,
        'source' => 'subject',
    ]),
);
source_policy_check(
    $subjectTraining['eligibility']['available'] === true,
    'Subject training was incorrectly forced through smart-guide validation.',
);

$subjectSmartReview = api_activity_apply_authoritative_policy(
    $pdo,
    42,
    source_policy_preview([
        'subject_version_id' => 12,
        'unit_id' => null,
        'lesson_id' => null,
        'activity_type' => 'smart_review',
        'activity_mode' => 'review',
        'guide_step_id' => null,
        'source' => 'review',
    ]),
);
source_policy_check(
    $subjectSmartReview['eligibility']['available'] === true,
    'Subject-level smart review was incorrectly forced to provide a unit.',
);

$guideMismatch = api_activity_apply_authoritative_policy(
    $pdo,
    42,
    source_policy_preview([
        'subject_version_id' => 12,
        'unit_id' => null,
        'lesson_id' => null,
        'activity_type' => 'choose_test',
        'activity_mode' => 'practice',
        'guide_step_id' => 91,
        'source' => 'home_guide',
    ]),
);
source_policy_check(
    $guideMismatch['eligibility']['available'] === false
        && $guideMismatch['eligibility']['reason_code'] === 'invalid_guide_action',
    'Guide requests no longer enforce the authoritative guide action.',
);

$compatSource = file_get_contents(
    dirname(__DIR__) . '/public_html/api/_activity_preparation_compat.php',
);
if ($compatSource === false) {
    throw new RuntimeException('Unable to read compatible activity preparation source.');
}
foreach ([
    'api_activity_training_tool($pdo, $studentId, $request)',
    "'training_source_unavailable'",
    "'training_empty'",
    "'question_count' => \$confirmedCount",
] as $required) {
    source_policy_check(
        str_contains($compatSource, $required),
        "Compatible activity preview is missing training revalidation: {$required}",
    );
}

echo "Activity preparation source-policy tests passed.\n";
