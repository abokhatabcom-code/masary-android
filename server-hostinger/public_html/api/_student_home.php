<?php
declare(strict_types=1);

require_once __DIR__ . '/_tokens.php';

$dashboardHelpers = IKHTABIRNI_PUBLIC_ROOT . '/includes/student_dashboard_optimization.php';
if (!is_file($dashboardHelpers)) {
    throw new RuntimeException('Student dashboard helpers are not installed.');
}
require_once $dashboardHelpers;

function api_student_home_unread_count(PDO $pdo, int $studentId): int
{
    try {
        $visible = function_exists('notifications_visible_type_sql')
            ? notifications_visible_type_sql('type')
            : "type <> 'challenge_new' AND type NOT LIKE 'challenge_%' AND type NOT LIKE 'friend_duel_%' "
              . "AND type NOT IN ('friend_overtaken','friend_close_to_lead')";
        $stmt = $pdo->prepare("SELECT COUNT(*) FROM app_notifications WHERE user_id=? AND is_read=0 AND {$visible}");
        $stmt->execute([$studentId]);
        return max(0, (int)($stmt->fetchColumn() ?: 0));
    } catch (Throwable) {
        return 0;
    }
}

function api_student_home_subject_names(PDO $pdo, array $subjectVersionIds): array
{
    $ids = array_values(array_unique(array_filter(array_map('intval', $subjectVersionIds), static fn(int $id): bool => $id > 0)));
    if ($ids === []) {
        return [];
    }

    $placeholders = implode(',', array_fill(0, count($ids), '?'));
    $stmt = $pdo->prepare(
        "SELECT sv.id AS subject_version_id, s.name AS subject_name "
        . "FROM subject_versions sv JOIN subjects s ON s.id=sv.subject_id "
        . "WHERE sv.id IN ({$placeholders})"
    );
    $stmt->execute($ids);

    $out = [];
    foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
        $out[(int)$row['subject_version_id']] = trim((string)($row['subject_name'] ?? ''));
    }
    return $out;
}

function api_student_home_subjects(PDO $pdo, int $studentId): array
{
    try {
        $stmt = $pdo->prepare(
            "SELECT ss.subject_version_id, s.name, ss.hearts "
            . "FROM student_subject_state ss "
            . "JOIN subject_versions sv ON sv.id=ss.subject_version_id "
            . "JOIN subjects s ON s.id=sv.subject_id "
            . "WHERE ss.student_id=? ORDER BY s.name, ss.subject_version_id LIMIT 12"
        );
        $stmt->execute([$studentId]);
        $items = [];
        foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
            $subjectVersionId = max(0, (int)($row['subject_version_id'] ?? 0));
            $progress = function_exists('ik_dash_subject_progress')
                ? (int)ik_dash_subject_progress($pdo, $studentId, $subjectVersionId)
                : 0;
            $items[] = [
                'subject_version_id' => $subjectVersionId,
                'name' => trim((string)($row['name'] ?? '')),
                'hearts' => max(0, (int)($row['hearts'] ?? 0)),
                'progress_percent' => max(0, min(100, $progress)),
            ];
        }
        return $items;
    } catch (Throwable) {
        return [];
    }
}

function api_student_home_spotlight(PDO $pdo, int $studentId): ?array
{
    if (!function_exists('ik_dash_home_spotlight')) {
        return null;
    }
    $item = ik_dash_home_spotlight($pdo, $studentId);
    if (!is_array($item) || trim((string)($item['title'] ?? '')) === '') {
        return null;
    }
    return [
        'type' => in_array(($item['type'] ?? ''), ['news', 'offer'], true) ? $item['type'] : 'news',
        'title' => trim((string)$item['title']),
        'body' => trim((string)($item['body'] ?? '')),
        'cta_label' => trim((string)($item['cta_label'] ?? '')),
        'cta_url' => trim((string)($item['cta_url'] ?? '')),
    ];
}

function api_student_home_continue(PDO $pdo, int $studentId): array
{
    $empty = [
        'available' => false,
        'subject_version_id' => null,
        'subject_name' => '',
        'unit_id' => null,
        'unit_title' => '',
        'mode' => 'learn',
        'label' => 'ابدأ من المواد',
        'hint' => 'اختر مادة ثم ابدأ التعلّم',
        'disabled' => false,
        'disabled_reason' => '',
        'hearts' => null,
        'updated_at' => '',
    ];

    try {
        $stmt = $pdo->prepare(
            "SELECT la.subject_version_id, la.unit_id, la.mode, la.updated_at, "
            . "s.name AS subject_name, u.title AS unit_title, ss.hearts "
            . "FROM student_last_activity la "
            . "JOIN subject_versions sv ON sv.id=la.subject_version_id "
            . "JOIN subjects s ON s.id=sv.subject_id "
            . "LEFT JOIN units u ON u.id=la.unit_id "
            . "LEFT JOIN student_subject_state ss ON ss.student_id=la.student_id AND ss.subject_version_id=la.subject_version_id "
            . "WHERE la.student_id=? LIMIT 1"
        );
        $stmt->execute([$studentId]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC) ?: null;
        if (!$row) {
            return $empty;
        }

        $subjectVersionId = (int)($row['subject_version_id'] ?? 0);
        if ($subjectVersionId <= 0) {
            return $empty;
        }

        $unitId = (int)($row['unit_id'] ?? 0);
        $mode = (string)($row['mode'] ?? 'learn');
        $subjectName = trim((string)($row['subject_name'] ?? ''));
        $unitTitle = trim((string)($row['unit_title'] ?? ''));
        $hearts = $row['hearts'] !== null ? max(0, (int)$row['hearts']) : 3;
        $label = 'تابع المادة';
        $hint = $subjectName !== '' ? ('آخر مادة: ' . $subjectName) : 'آخر مادة';
        $disabled = false;
        $disabledReason = '';

        if ($unitId > 0) {
            $hint = trim(($subjectName !== '' ? ($subjectName . ' — ') : '') . $unitTitle);
            $label = $mode === 'review' ? 'تابع المراجعة' : 'تابع التعلّم';
            if ($mode === 'review' && $hearts <= 0) {
                $label = 'اشحن القلوب';
                $disabled = true;
                $disabledReason = 'لا توجد قلوب للمراجعة اليوم — اشحن القلوب من صفحة المادة';
            }
        }

        return [
            'available' => true,
            'subject_version_id' => $subjectVersionId,
            'subject_name' => $subjectName,
            'unit_id' => $unitId > 0 ? $unitId : null,
            'unit_title' => $unitTitle,
            'mode' => $mode,
            'label' => $label,
            'hint' => $hint,
            'disabled' => $disabled,
            'disabled_reason' => $disabledReason,
            'hearts' => $hearts,
            'updated_at' => (string)($row['updated_at'] ?? ''),
        ];
    } catch (Throwable) {
        return $empty;
    }
}

function api_student_home_smart_guide(PDO $pdo, int $studentId): array
{
    $settings = function_exists('ik_dash_study_guide_settings')
        ? (array)ik_dash_study_guide_settings($pdo)
        : [];
    $enabled = !empty($settings['feature_enabled']) && !empty($settings['show_on_dashboard']);

    if (!$enabled) {
        return [
            'enabled' => false,
            'status' => 'disabled',
            'guide_id' => null,
            'guide_date' => function_exists('student_study_guide_today_key') ? student_study_guide_today_key() : date('Y-m-d'),
            'headline' => 'الموجّه الدراسي غير مفعّل حاليًا',
            'intro_text' => '',
            'boost_note' => '',
            'profile_key' => '',
            'completion_percent' => 0,
            'completed_steps' => 0,
            'total_steps' => 0,
            'is_complete' => false,
            'updated_at' => '',
            'steps' => [],
        ];
    }

    $date = function_exists('student_study_guide_today_key') ? student_study_guide_today_key() : date('Y-m-d');
    $guide = function_exists('ik_dash_study_guide_load')
        ? ik_dash_study_guide_load($pdo, $studentId, $date)
        : null;

    if (!$guide) {
        return [
            'enabled' => true,
            'status' => 'not_generated',
            'guide_id' => null,
            'guide_date' => $date,
            'headline' => 'الموجّه الدراسي لم يُجهّز بعد',
            'intro_text' => 'ستظهر خطتك الذكية هنا فور تجهيزها من النظام.',
            'boost_note' => '',
            'profile_key' => '',
            'completion_percent' => 0,
            'completed_steps' => 0,
            'total_steps' => 0,
            'is_complete' => false,
            'updated_at' => '',
            'steps' => [],
        ];
    }

    $rawSteps = is_array($guide['steps'] ?? null) ? $guide['steps'] : [];
    $subjectNames = api_student_home_subject_names(
        $pdo,
        array_map(static fn(array $step): int => (int)($step['subject_version_id'] ?? 0), $rawSteps),
    );

    $steps = [];
    foreach ($rawSteps as $step) {
        $meta = is_array($step['meta'] ?? null)
            ? $step['meta']
            : (function_exists('student_study_guide_decode_json')
                ? student_study_guide_decode_json((string)($step['meta_json'] ?? '{}'))
                : []);
        $subjectVersionId = (int)($step['subject_version_id'] ?? 0);
        $subjectName = trim((string)($step['subject_name'] ?? ($meta['subject_name'] ?? ($subjectNames[$subjectVersionId] ?? ''))));

        $steps[] = [
            'id' => (int)($step['id'] ?? 0),
            'sort_order' => (int)($step['sort_order'] ?? 0),
            'subject_version_id' => $subjectVersionId,
            'subject_name' => $subjectName,
            'unit_id' => ((int)($step['unit_id'] ?? 0)) > 0 ? (int)$step['unit_id'] : null,
            'part' => max(0, (int)($step['part'] ?? 0)),
            'action_key' => (string)($step['action_key'] ?? ''),
            'action_group' => (string)($step['action_group'] ?? 'primary'),
            'title' => trim((string)($step['title'] ?? '')),
            'subtitle' => trim((string)($step['subtitle'] ?? '')),
            'reason_text' => trim((string)($step['reason_text'] ?? '')),
            'cta_label' => trim((string)($step['cta_label'] ?? 'ابدأ الآن')),
            'estimated_minutes' => max(0, (int)($step['estimated_minutes'] ?? 0)),
            'reward_gems' => max(0, (int)($step['reward_gems'] ?? 0)),
            'progress_state' => (string)($step['progress_state'] ?? 'pending'),
            'completed_at' => (string)($step['completed_at'] ?? ''),
        ];
    }

    $meta = is_array($guide['meta'] ?? null)
        ? $guide['meta']
        : (function_exists('student_study_guide_decode_json')
            ? student_study_guide_decode_json((string)($guide['meta_json'] ?? '{}'))
            : []);
    $isComplete = !empty($guide['is_complete']);

    return [
        'enabled' => true,
        'status' => $isComplete ? 'completed' : 'ready',
        'guide_id' => (int)($guide['id'] ?? 0),
        'guide_date' => (string)($guide['guide_date'] ?? $date),
        'headline' => trim((string)($meta['headline'] ?? 'الموجّه الدراسي جاهز')),
        'intro_text' => trim((string)($meta['intro_text'] ?? '')),
        'boost_note' => trim((string)($meta['boost_note'] ?? '')),
        'profile_key' => (string)($guide['profile_key'] ?? ''),
        'completion_percent' => max(0, min(100, (int)($guide['completion_percent'] ?? 0))),
        'completed_steps' => max(0, (int)($guide['completed_steps'] ?? count(array_filter($steps, static fn(array $step): bool => $step['progress_state'] === 'completed')))),
        'total_steps' => max(0, (int)($guide['total_steps'] ?? count($steps))),
        'is_complete' => $isComplete,
        'updated_at' => (string)($guide['updated_at'] ?? ''),
        'steps' => $steps,
    ];
}

function api_student_home_payload(PDO $pdo, array $session): array
{
    $studentId = (int)($session['user_id'] ?? 0);
    if ($studentId <= 0) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }

    $profile = function_exists('ik_dash_profile')
        ? (array)ik_dash_profile($pdo, $studentId)
        : [];
    $today = function_exists('ik_dash_today_stats')
        ? (array)ik_dash_today_stats($pdo, $studentId)
        : ['xp' => 0, 'seconds' => 0, 'minutes' => 0, 'attempts' => 0];
    $subscription = function_exists('ik_dash_subscription')
        ? (array)ik_dash_subscription($pdo, $studentId)
        : ['status' => 'غير نشط', 'ends_at' => ''];
    $continue = api_student_home_continue($pdo, $studentId);
    $smartGuide = api_student_home_smart_guide($pdo, $studentId);
    $subjects = api_student_home_subjects($pdo, $studentId);
    $spotlight = api_student_home_spotlight($pdo, $studentId);
    $globalRank = function_exists('ik_dash_global_rank')
        ? max(0, (int)ik_dash_global_rank($pdo, $studentId))
        : max(0, (int)($profile['global_rank'] ?? 0));

    $globalXp = max(0.0, (float)($profile['global_xp'] ?? 0));
    $levelStep = 300.0;
    $levelMax = 10;
    $level = min($levelMax, (int)floor($globalXp / $levelStep) + 1);
    $levelBase = ($level - 1) * $levelStep;
    $levelNext = $level * $levelStep;
    $levelPercent = $level >= $levelMax
        ? 100
        : (int)round(max(0.0, min(1.0, ($globalXp - $levelBase) / $levelStep)) * 100);

    $streak = function_exists('student_streak_dashboard_state')
        ? (array)student_streak_dashboard_state($profile, function_exists('ik_dash_today_key') ? ik_dash_today_key() : null, $pdo)
        : [
            'current_days' => max(0, (int)($profile['streak_days'] ?? 0)),
            'best_days' => max(0, (int)($profile['streak_days'] ?? 0)),
            'protection_count' => 0,
            'protection_max' => 3,
            'next_milestone' => 3,
            'checkpoint_days' => 0,
            'status' => 'start',
            'message' => 'ابدأ اليوم بخطوة تعليمية.',
            'goal' => [],
        ];
    $goal = is_array($streak['goal'] ?? null) ? $streak['goal'] : [];

    $activeSubscription = (string)($subscription['status'] ?? '') === 'نشط' && trim((string)($subscription['ends_at'] ?? '')) !== '';
    $unreadCount = api_student_home_unread_count($pdo, $studentId);

    $versionParts = [
        $studentId,
        (int)floor($globalXp),
        (int)($profile['gems'] ?? 0),
        (int)($streak['current_days'] ?? 0),
        (int)($streak['best_days'] ?? 0),
        (int)($today['xp'] ?? 0),
        (int)($today['seconds'] ?? 0),
        (int)($today['attempts'] ?? 0),
        (string)($smartGuide['updated_at'] ?? ''),
        (string)($continue['updated_at'] ?? ''),
        $unreadCount,
        (string)($subscription['ends_at'] ?? ''),
        $globalRank,
        sha1(json_encode($subjects, JSON_UNESCAPED_UNICODE) ?: ''),
        sha1(json_encode($spotlight, JSON_UNESCAPED_UNICODE) ?: ''),
    ];

    return [
        'version' => sha1(implode('|', $versionParts)),
        'generated_at' => gmdate(DATE_ATOM),
        'student' => [
            'id' => (string)$studentId,
            'username' => (string)($session['username'] ?? ''),
            'display_name' => (string)($session['full_name'] ?? ''),
            'avatar_path' => $session['avatar_path'] !== null ? (string)$session['avatar_path'] : null,
        ],
        'summary' => [
            'global_xp' => (int)floor($globalXp),
            'gems' => max(0, (int)($profile['gems'] ?? 0)),
            'level' => $level,
            'level_percent' => $levelPercent,
            'level_next_xp' => (int)$levelNext,
        ],
        'streak' => [
            'current_days' => max(0, (int)($streak['current_days'] ?? 0)),
            'best_days' => max(0, (int)($streak['best_days'] ?? 0)),
            'protection_count' => max(0, (int)($streak['protection_count'] ?? 0)),
            'protection_max' => max(0, (int)($streak['protection_max'] ?? 0)),
            'next_milestone' => max(0, (int)($streak['next_milestone'] ?? 0)),
            'checkpoint_days' => max(0, (int)($streak['checkpoint_days'] ?? 0)),
            'status' => (string)($streak['status'] ?? 'start'),
            'message' => (string)($streak['message'] ?? ''),
            'goal' => [
                'days' => max(0, (int)($goal['days'] ?? 0)),
                'status' => (string)($goal['status'] ?? 'none'),
                'is_completed' => !empty($goal['is_completed']),
                'remaining_days' => max(0, (int)($goal['remaining_days'] ?? 0)),
                'progress_percent' => max(0, min(100, (int)($goal['progress_percent'] ?? 0))),
                'gems' => max(0, (int)($goal['gems'] ?? 0)),
                'shields' => max(0, (int)($goal['shields'] ?? 0)),
                'label' => (string)($goal['label'] ?? ''),
            ],
        ],
        'today' => [
            'xp' => max(0, (int)($today['xp'] ?? 0)),
            'seconds' => max(0, (int)($today['seconds'] ?? 0)),
            'minutes' => max(0, (int)($today['minutes'] ?? 0)),
            'attempts' => max(0, (int)($today['attempts'] ?? 0)),
        ],
        'subscription' => [
            'active' => $activeSubscription,
            'status' => (string)($subscription['status'] ?? 'غير نشط'),
            'ends_at' => (string)($subscription['ends_at'] ?? ''),
        ],
        'notifications' => [
            'unread_count' => $unreadCount,
        ],
        'continue_learning' => $continue,
        'smart_guide' => $smartGuide,
        'indicators' => [
            'total_xp' => (int)floor($globalXp),
            'gems' => max(0, (int)($profile['gems'] ?? 0)),
            'streak_days' => max(0, (int)($streak['current_days'] ?? 0)),
            'global_rank' => $globalRank > 0 ? $globalRank : null,
        ],
        'subjects' => $subjects,
        'spotlight' => $spotlight,
    ];
}
