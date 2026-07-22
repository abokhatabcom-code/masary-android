<?php
declare(strict_types=1);

/** Android Notifications V1 only. Encryption key is derived from the server secret outside Git. */
function api_android_push_key(): string { return hash_hkdf('sha256', api_server_secret(), 32, 'masary-android-fcm-v1'); }
function api_encrypt_fcm_token(string $token): string {
    $iv=random_bytes(12); $tag='';
    $cipher=openssl_encrypt($token,'aes-256-gcm',api_android_push_key(),OPENSSL_RAW_DATA,$iv,$tag,'android-notifications-v1',16);
    if($cipher===false) throw new RuntimeException('Unable to protect notification credential.');
    return base64_encode(chr(1).$iv.$tag.$cipher); // versioned envelope supports future key rotation/re-encryption.
}
function api_android_installation_payload(array $payload,bool $tokenRequired): array {
    $installation=trim((string)($payload['installation_id']??'')); $token=trim((string)($payload['fcm_token']??''));
    if(!preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i',$installation)) api_error('invalid_installation','معرف التثبيت غير صالح.',422);
    if($tokenRequired && (strlen($token)<32||strlen($token)>4096||preg_match('/\s/',$token))) api_error('invalid_push_token','رمز الإشعارات غير صالح.',422);
    $platform=trim((string)($payload['platform']??'')); if($platform!=='android') api_error('invalid_platform','المنصة غير صالحة.',422);
    $status=trim((string)($payload['permission_status']??''));
    if(!in_array($status,['NotRequired','NotRequested','Denied','PermanentlyDenied','Granted'],true)) api_error('invalid_permission_status','حالة الإذن غير صالحة.',422);
    return ['installation_id'=>$installation,'token'=>$token,'app_version'=>substr(trim((string)($payload['app_version']??'')),0,40),'app_build'=>(int)($payload['app_build']??0),'platform'=>$platform,'locale'=>substr(trim((string)($payload['locale']??'')),0,35),'timezone'=>substr(trim((string)($payload['timezone']??'')),0,64),'permission_status'=>$status];
}
function api_upsert_android_installation(PDO $pdo,int $userId,array $payload): void {
    $hasToken=trim((string)($payload['fcm_token']??''))!==''; $v=api_android_installation_payload($payload,$hasToken); $now=api_mysql_datetime(time());
    $pdo->beginTransaction();
    try {
        if(!$hasToken) {
            $stmt=$pdo->prepare('UPDATE api_android_push_installations SET app_version=?,app_build=?,locale=?,timezone=?,permission_status=?,last_seen_at=?,updated_at=? WHERE installation_id=? AND user_id=? AND disabled_at IS NULL');
            $stmt->execute([$v['app_version'],$v['app_build'],$v['locale'],$v['timezone'],$v['permission_status'],$now,$now,$v['installation_id'],$userId]);
            if($stmt->rowCount()===0) api_error('fcm_token_required','رمز الإشعارات مطلوب للتسجيل الأول.',422);
        } else {
            $hash=hash('sha256',$v['token']); $encrypted=api_encrypt_fcm_token($v['token']);
            $pdo->prepare('UPDATE api_android_push_installations SET encrypted_fcm_token=NULL,token_hash=NULL,disabled_at=?,updated_at=? WHERE token_hash=? AND installation_id<>?')->execute([$now,$now,$hash,$v['installation_id']]);
            $sql='INSERT INTO api_android_push_installations (installation_id,user_id,encrypted_fcm_token,token_hash,app_version,app_build,platform,locale,timezone,permission_status,last_seen_at,created_at,updated_at,disabled_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,NULL) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),encrypted_fcm_token=VALUES(encrypted_fcm_token),token_hash=VALUES(token_hash),app_version=VALUES(app_version),app_build=VALUES(app_build),platform=VALUES(platform),locale=VALUES(locale),timezone=VALUES(timezone),permission_status=VALUES(permission_status),last_seen_at=VALUES(last_seen_at),updated_at=VALUES(updated_at),disabled_at=NULL';
            $pdo->prepare($sql)->execute([$v['installation_id'],$userId,$encrypted,$hash,$v['app_version'],$v['app_build'],$v['platform'],$v['locale'],$v['timezone'],$v['permission_status'],$now,$now,$now]);
        }
        $pdo->commit();
    } catch(Throwable $error) { if($pdo->inTransaction()) $pdo->rollBack(); throw $error; }
}
function api_disable_android_installation(PDO $pdo,int $userId,array $payload): void {
    $v=api_android_installation_payload($payload,false); $now=api_mysql_datetime(time());
    $pdo->prepare('UPDATE api_android_push_installations SET user_id=NULL,encrypted_fcm_token=NULL,token_hash=NULL,permission_status=?,last_seen_at=?,updated_at=?,disabled_at=? WHERE installation_id=? AND user_id=?')->execute([$v['permission_status'],$now,$now,$now,$v['installation_id'],$userId]);
}
