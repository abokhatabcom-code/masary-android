<?php
declare(strict_types=1);

require_once __DIR__ . '/_database.php';

function api_base64url_encode(string $bytes): string
{
    return rtrim(strtr(base64_encode($bytes), '+/', '-_'), '=');
}

function api_server_secret(): string
{
    static $secret = null;
    if (is_string($secret)) {
        return $secret;
    }

    $dir = IKHTABIRNI_DATA_DIR . '/api';
    if (!is_dir($dir) && !@mkdir($dir, 0700, true) && !is_dir($dir)) {
        throw new RuntimeException('Unable to create API secret directory.');
    }
    $path = $dir . '/token_pepper.key';

    if (is_file($path)) {
        $raw = trim((string)@file_get_contents($path));
        $decoded = base64_decode($raw, true);
        if (is_string($decoded) && strlen($decoded) >= 32) {
            $secret = $decoded;
            return $secret;
        }
        throw new RuntimeException('Invalid API token pepper.');
    }

    $generated = random_bytes(48);
    $tmp = $path . '.' . bin2hex(random_bytes(6)) . '.tmp';
    if (@file_put_contents($tmp, base64_encode($generated), LOCK_EX) === false) {
        throw new RuntimeException('Unable to persist API token pepper.');
    }
    @chmod($tmp, 0600);
    if (!@rename($tmp, $path)) {
        @unlink($tmp);
        if (is_file($path)) {
            return api_server_secret();
        }
        throw new RuntimeException('Unable to install API token pepper.');
    }
    @chmod($path, 0600);
    $secret = $generated;
    return $secret;
}

function api_token(string $prefix): string
{
    return $prefix . '.' . api_base64url_encode(random_bytes(48));
}

function api_token_hash(string $token): string
{
    return hash_hmac('sha256', $token, api_server_secret());
}

function api_mysql_datetime(int $timestamp): string
{
    return gmdate('Y-m-d H:i:s', $timestamp);
}

function api_datetime_to_timestamp(string $value): int
{
    $date = DateTimeImmutable::createFromFormat('!Y-m-d H:i:s', $value, new DateTimeZone('UTC'));
    return $date instanceof DateTimeImmutable ? $date->getTimestamp() : 0;
}

function api_log_auth_event(PDO $pdo, string $eventType, ?int $sessionId, ?int $userId, array $metadata = []): void
{
    try {
        $stmt = $pdo->prepare(
            'INSERT INTO api_auth_events(session_id,user_id,event_type,ip_address,user_agent,metadata_json,created_at) VALUES(?,?,?,?,?,?,?)'
        );
        $stmt->execute([
            $sessionId,
            $userId,
            function_exists('mb_substr') ? mb_substr($eventType, 0, 64, 'UTF-8') : substr($eventType, 0, 64),
            api_client_ip(),
            api_user_agent(),
            $metadata === [] ? null : json_encode($metadata, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            api_mysql_datetime(time()),
        ]);
    } catch (Throwable) {
        // Authentication must not fail only because audit logging is unavailable.
    }
}

function api_cleanup_expired_sessions(PDO $pdo): void
{
    if (random_int(1, 100) !== 1) {
        return;
    }
    $cutoff = api_mysql_datetime(time() - 86400);
    $stmt = $pdo->prepare('DELETE FROM api_auth_sessions WHERE refresh_expires_at < ? OR (revoked_at IS NOT NULL AND revoked_at < ?)');
    $stmt->execute([$cutoff, api_mysql_datetime(time() - 2592000)]);
}

function api_enforce_session_limit(PDO $pdo, int $userId): void
{
    $stmt = $pdo->prepare(
        'SELECT id FROM api_auth_sessions WHERE user_id=? AND revoked_at IS NULL AND refresh_expires_at>? ORDER BY created_at DESC, id DESC'
    );
    $stmt->execute([$userId, api_mysql_datetime(time())]);
    $ids = array_map('intval', $stmt->fetchAll(PDO::FETCH_COLUMN) ?: []);
    if (count($ids) <= MASARY_MAX_ACTIVE_SESSIONS) {
        return;
    }
    $revokeIds = array_slice($ids, MASARY_MAX_ACTIVE_SESSIONS);
    $placeholders = implode(',', array_fill(0, count($revokeIds), '?'));
    $pdo->prepare("UPDATE api_auth_sessions SET revoked_at=?, revoked_reason=?, updated_at=? WHERE id IN ($placeholders)")
        ->execute(array_merge([api_mysql_datetime(time()), 'session_limit', api_mysql_datetime(time())], $revokeIds));
}

function api_issue_session(PDO $pdo, array $user, string $deviceName): array
{
    api_cleanup_expired_sessions($pdo);

    $accessToken = api_token('msa');
    $refreshToken = api_token('msr');
    $now = time();
    $accessExpiresAt = api_mysql_datetime($now + MASARY_ACCESS_TTL_SECONDS);
    $refreshExpiresAt = api_mysql_datetime($now + MASARY_REFRESH_TTL_SECONDS);

    $stmt = $pdo->prepare(
        'INSERT INTO api_auth_sessions(user_id,role,device_name,access_token_hash,refresh_token_hash,access_expires_at,refresh_expires_at,ip_address,user_agent,created_at,updated_at,last_used_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)'
    );
    $stmt->execute([
        (int)$user['id'],
        'student',
        api_device_name($deviceName),
        api_token_hash($accessToken),
        api_token_hash($refreshToken),
        $accessExpiresAt,
        $refreshExpiresAt,
        api_client_ip(),
        api_user_agent(),
        api_mysql_datetime($now),
        api_mysql_datetime($now),
        api_mysql_datetime($now),
    ]);
    $sessionId = (int)$pdo->lastInsertId();
    api_enforce_session_limit($pdo, (int)$user['id']);
    api_log_auth_event($pdo, 'login_success', $sessionId, (int)$user['id'], ['device_name' => api_device_name($deviceName)]);

    return [
        'session_id' => $sessionId,
        'access_token' => $accessToken,
        'refresh_token' => $refreshToken,
        'expires_in' => MASARY_ACCESS_TTL_SECONDS,
    ];
}

function api_active_student_clause(): string
{
    return "u.role='student' AND COALESCE(u.is_active,0)=1 AND COALESCE(u.manual_status,'active') NOT IN ('suspended','archived')";
}

function api_authenticate_access_token(PDO $pdo, string $accessToken): array
{
    if ($accessToken === '' || strlen($accessToken) > 256) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة.', 401);
    }
    $stmt = $pdo->prepare(
        'SELECT s.*, u.username, u.full_name, u.city_id, u.school_id, u.grade_id, u.avatar_path, u.student_code, u.is_active, u.manual_status '
        . 'FROM api_auth_sessions s JOIN app_users u ON u.id=s.user_id '
        . 'WHERE s.access_token_hash=? AND s.revoked_at IS NULL AND s.access_expires_at>? AND ' . api_active_student_clause() . ' LIMIT 1'
    );
    $stmt->execute([api_token_hash($accessToken), api_mysql_datetime(time())]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) {
        api_error('unauthorized', 'جلسة الدخول غير صالحة أو منتهية.', 401);
    }
    $pdo->prepare('UPDATE api_auth_sessions SET last_used_at=?, updated_at=? WHERE id=?')
        ->execute([api_mysql_datetime(time()), api_mysql_datetime(time()), (int)$row['id']]);
    return $row;
}

function api_rotate_refresh_token(PDO $pdo, string $refreshToken): array
{
    if ($refreshToken === '' || strlen($refreshToken) > 256) {
        api_error('invalid_refresh_token', 'رمز تحديث الجلسة غير صالح.', 401);
    }
    $hash = api_token_hash($refreshToken);
    $stmt = $pdo->prepare(
        'SELECT s.*, u.username, u.full_name, u.city_id, u.school_id, u.grade_id, u.avatar_path, u.student_code, u.is_active, u.manual_status '
        . 'FROM api_auth_sessions s JOIN app_users u ON u.id=s.user_id '
        . 'WHERE (s.refresh_token_hash=? OR s.previous_refresh_token_hash=?) AND s.revoked_at IS NULL AND ' . api_active_student_clause() . ' LIMIT 1'
    );
    $stmt->execute([$hash, $hash]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) {
        api_error('invalid_refresh_token', 'رمز تحديث الجلسة غير صالح.', 401);
    }

    $now = time();
    if (hash_equals((string)($row['previous_refresh_token_hash'] ?? ''), $hash)) {
        $pdo->prepare('UPDATE api_auth_sessions SET revoked_at=?, revoked_reason=?, updated_at=? WHERE id=?')
            ->execute([api_mysql_datetime($now), 'refresh_reuse', api_mysql_datetime($now), (int)$row['id']]);
        api_log_auth_event($pdo, 'refresh_reuse_detected', (int)$row['id'], (int)$row['user_id']);
        api_error('invalid_refresh_token', 'تم إبطال الجلسة لأسباب أمنية. سجّل الدخول من جديد.', 401);
    }

    if (api_datetime_to_timestamp((string)$row['refresh_expires_at']) <= $now) {
        $pdo->prepare('UPDATE api_auth_sessions SET revoked_at=?, revoked_reason=?, updated_at=? WHERE id=?')
            ->execute([api_mysql_datetime($now), 'refresh_expired', api_mysql_datetime($now), (int)$row['id']]);
        api_error('refresh_expired', 'انتهت جلسة الدخول. سجّل الدخول من جديد.', 401);
    }

    $newAccess = api_token('msa');
    $newRefresh = api_token('msr');
    $newAccessExpires = api_mysql_datetime($now + MASARY_ACCESS_TTL_SECONDS);
    $newRefreshExpires = api_mysql_datetime($now + MASARY_REFRESH_TTL_SECONDS);

    $pdo->beginTransaction();
    try {
        $update = $pdo->prepare(
            'UPDATE api_auth_sessions SET previous_refresh_token_hash=refresh_token_hash, previous_refresh_expires_at=refresh_expires_at, access_token_hash=?, refresh_token_hash=?, access_expires_at=?, refresh_expires_at=?, ip_address=?, user_agent=?, last_used_at=?, updated_at=? WHERE id=? AND refresh_token_hash=? AND revoked_at IS NULL'
        );
        $update->execute([
            api_token_hash($newAccess),
            api_token_hash($newRefresh),
            $newAccessExpires,
            $newRefreshExpires,
            api_client_ip(),
            api_user_agent(),
            api_mysql_datetime($now),
            api_mysql_datetime($now),
            (int)$row['id'],
            $hash,
        ]);
        if ($update->rowCount() !== 1) {
            throw new RuntimeException('Concurrent refresh detected.');
        }
        $pdo->commit();
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) {
            $pdo->rollBack();
        }
        throw $e;
    }

    api_log_auth_event($pdo, 'refresh_success', (int)$row['id'], (int)$row['user_id']);
    return [
        'access_token' => $newAccess,
        'refresh_token' => $newRefresh,
        'expires_in' => MASARY_ACCESS_TTL_SECONDS,
        'student' => api_student_payload($row),
    ];
}

function api_revoke_session(PDO $pdo, ?int $sessionId, string $refreshToken = ''): void
{
    $now = api_mysql_datetime(time());
    if ($sessionId !== null && $sessionId > 0) {
        $stmt = $pdo->prepare('UPDATE api_auth_sessions SET revoked_at=?, revoked_reason=?, updated_at=? WHERE id=? AND revoked_at IS NULL');
        $stmt->execute([$now, 'logout', $now, $sessionId]);
        return;
    }
    if ($refreshToken !== '') {
        $stmt = $pdo->prepare('UPDATE api_auth_sessions SET revoked_at=?, revoked_reason=?, updated_at=? WHERE refresh_token_hash=? AND revoked_at IS NULL');
        $stmt->execute([$now, 'logout', $now, api_token_hash($refreshToken)]);
    }
}

function api_student_payload(array $row): array
{
    return [
        'id' => (string)$row['user_id'],
        'username' => (string)$row['username'],
        'display_name' => (string)($row['full_name'] ?? ''),
        'city_id' => $row['city_id'] !== null ? (int)$row['city_id'] : null,
        'school_id' => $row['school_id'] !== null ? (int)$row['school_id'] : null,
        'grade_id' => $row['grade_id'] !== null ? (int)$row['grade_id'] : null,
        'student_code' => $row['student_code'] !== null ? (string)$row['student_code'] : null,
        'avatar_path' => $row['avatar_path'] !== null ? (string)$row['avatar_path'] : null,
    ];
}
