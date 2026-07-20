<?php
declare(strict_types=1);

require_once __DIR__ . '/_tokens.php';

function api_find_student_for_login(PDO $pdo, string $username): ?array
{
    $stmt = $pdo->prepare(
        'SELECT id, role, full_name, username, password_hash, is_active, manual_status, city_id, school_id, grade_id, avatar_path, student_code '
        . 'FROM app_users WHERE username=? LIMIT 1'
    );
    $stmt->execute([$username]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    return is_array($row) ? $row : null;
}

function api_student_can_login(?array $row, string $password): bool
{
    if (!$row || (string)($row['role'] ?? '') !== 'student') {
        password_verify($password, '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi');
        return false;
    }
    $validPassword = password_verify($password, (string)($row['password_hash'] ?? ''));
    $active = (int)($row['is_active'] ?? 0) === 1;
    $status = strtolower((string)($row['manual_status'] ?? 'active'));
    return $validPassword && $active && !in_array($status, ['suspended', 'archived'], true);
}
