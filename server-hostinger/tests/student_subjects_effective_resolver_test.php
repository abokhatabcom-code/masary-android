<?php
declare(strict_types=1);

$GLOBALS['masary_test_resolved_subjects'] = [
    [
        'subject_id' => 11,
        'subject_name' => 'الكيمياء',
        'sv_id' => 101,
        'version_name' => 'منهج صنعاء',
    ],
    [
        'subject_id' => 12,
        'subject_name' => 'اللغة العربية',
        'sv_id' => 102,
        'version_name' => 'منهج صنعاء',
    ],
];
$GLOBALS['masary_test_resolver_scope'] = null;

function curriculum_effective_subjects(PDO $pdo, int $schoolId, int $gradeId, ?int $cityId = null): array
{
    $GLOBALS['masary_test_resolver_scope'] = [$schoolId, $gradeId, $cityId];
    return $GLOBALS['masary_test_resolved_subjects'];
}

require_once __DIR__ . '/../public_html/api/_student_home_subjects.php';

if (!in_array('sqlite', PDO::getAvailableDrivers(), true)) {
    throw new RuntimeException('PDO SQLite is required for the isolated subject resolver test.');
}

$pdo = new PDO('sqlite::memory:');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$pdo->exec("CREATE TABLE app_users (id INTEGER PRIMARY KEY, role TEXT, school_id INTEGER, grade_id INTEGER, city_id INTEGER)");
$pdo->exec("CREATE TABLE student_subject_state (student_id INTEGER, subject_version_id INTEGER, hearts INTEGER)");
$pdo->exec("CREATE TABLE subjects (id INTEGER PRIMARY KEY, name TEXT)");
$pdo->exec("CREATE TABLE subject_versions (id INTEGER PRIMARY KEY, subject_id INTEGER)");
$pdo->exec("INSERT INTO app_users(id,role,school_id,grade_id,city_id) VALUES(48,'student',77,5,3)");
$pdo->exec("INSERT INTO subjects(id,name) VALUES(11,'الكيمياء')");
$pdo->exec("INSERT INTO subject_versions(id,subject_id) VALUES(101,11)");
$pdo->exec("INSERT INTO student_subject_state(student_id,subject_version_id,hearts) VALUES(48,101,2)");

$subjects = api_student_home_curriculum_subjects($pdo, 48, 100);
if ($GLOBALS['masary_test_resolver_scope'] !== [77, 5, 3]) {
    throw new RuntimeException('The official resolver did not receive the student school/grade/city scope.');
}
if (count($subjects) !== 2) {
    throw new RuntimeException('The official resolver results were not returned.');
}
if (($subjects[0]['subject_version_id'] ?? 0) !== 101 || ($subjects[0]['name'] ?? '') !== 'الكيمياء') {
    throw new RuntimeException('The first resolved subject was mapped incorrectly.');
}
if (($subjects[0]['hearts'] ?? -1) !== 2) {
    throw new RuntimeException('Existing subject hearts were not attached to the resolved material.');
}
if (($subjects[1]['hearts'] ?? -1) !== 3) {
    throw new RuntimeException('A resolved material without state must receive the safe three-heart default.');
}

$limited = api_student_home_curriculum_subjects($pdo, 48, 1);
if (count($limited) !== 1 || ($limited[0]['subject_version_id'] ?? 0) !== 101) {
    throw new RuntimeException('The shared subject limit was not applied after official resolution.');
}

$GLOBALS['masary_test_resolved_subjects'] = [];
$authoritativeEmpty = api_student_home_curriculum_subjects($pdo, 48, 100);
if ($authoritativeEmpty !== []) {
    throw new RuntimeException('An authoritative empty curriculum must not leak legacy state from another scope.');
}

echo "Student subjects effective resolver tests passed.\n";
