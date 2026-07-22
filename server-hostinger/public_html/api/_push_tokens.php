<?php
declare(strict_types=1);

/** Android Notifications V1 adapter. It neither sends messages nor touches browser subscriptions. */
function api_validate_android_push_token(array $payload): array
{
    $token = trim((string)($payload['token'] ?? ''));
    $platform = trim((string)($payload['platform'] ?? 'android'));
    $app = trim((string)($payload['app'] ?? 'student'));
    if ($platform !== 'android' || $app !== 'student' || strlen($token) < 32 || strlen($token) > 4096 || preg_match('/\s/', $token)) {
        api_error('invalid_push_token', 'رمز الإشعارات غير صالح.', 422);
    }
    return [$token, $platform, $app];
}

function api_upsert_android_push_token(PDO $pdo, int $userId, array $payload): void
{
    [$token, $platform, $app] = api_validate_android_push_token($payload);
    $hash = hash('sha256', $token);
    $now = api_mysql_datetime(time());
    $stmt = $pdo->prepare('INSERT INTO api_android_push_devices (user_id,token,token_hash,platform,app_name,enabled,created_at,updated_at) VALUES (?,?,?,?,?,1,?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),token=VALUES(token),enabled=1,updated_at=VALUES(updated_at)');
    $stmt->execute([$userId, $token, $hash, $platform, $app, $now, $now]);
}

function api_delete_android_push_token(PDO $pdo, int $userId, array $payload): void
{
    [$token] = api_validate_android_push_token($payload);
    $stmt = $pdo->prepare('UPDATE api_android_push_devices SET enabled=0,token=NULL,updated_at=? WHERE user_id=? AND token_hash=?');
    $stmt->execute([api_mysql_datetime(time()), $userId, hash('sha256', $token)]);
}
