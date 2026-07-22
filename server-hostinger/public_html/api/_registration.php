<?php
declare(strict_types=1);
if (!defined('MASARY_REGISTRATION_TESTING')) { require_once __DIR__ . '/_tokens.php'; }

final class RegistrationApiException extends RuntimeException
{
    public function __construct(public readonly string $errorCode, public readonly int $status) { parent::__construct($errorCode); }
}

function api_registration_fail(string $code, string $message, int $status): never
{
    if (defined('MASARY_REGISTRATION_TESTING')) { throw new RegistrationApiException($code, $status); }
    api_error($code, $message, $status);
}
function api_registration_cities(PDO $pdo): array { $rows=$pdo->query("SELECT id,name,COALESCE(requires_school,1) requires_school FROM cities WHERE is_active=1 ORDER BY name ASC")->fetchAll(PDO::FETCH_ASSOC)?:[]; return array_map(static fn($r)=>['id'=>(int)$r['id'],'name'=>(string)$r['name'],'requires_school'=>(int)$r['requires_school']===1],$rows); }
function api_registration_grades(PDO $pdo): array { $rows=$pdo->query("SELECT id,name FROM grades WHERE is_active=1 AND COALESCE(student_visibility,'public')='public' ORDER BY sort_order ASC,name ASC")->fetchAll(PDO::FETCH_ASSOC)?:[]; return array_map(static fn($r)=>['id'=>(int)$r['id'],'name'=>(string)$r['name']],$rows); }
function api_registration_schools(PDO $pdo,int $cityId): array { $s=$pdo->prepare("SELECT s.id,s.name FROM schools s JOIN cities c ON c.id=s.city_id AND c.is_active=1 WHERE s.city_id=? AND s.is_active=1 AND COALESCE(s.student_visibility,'public')='public' ORDER BY s.directorate,s.name");$s->execute([$cityId]);return array_map(static fn($r)=>['id'=>(int)$r['id'],'name'=>(string)$r['name']],$s->fetchAll(PDO::FETCH_ASSOC)?:[]); }
function api_registration_validate(PDO $pdo,array $p): array {
 $v=['full_name'=>trim((string)($p['full_name']??'')),'username'=>trim((string)($p['username']??'')),'phone'=>trim((string)($p['phone']??'')),'email'=>trim((string)($p['email']??'')),'password'=>(string)($p['password']??''),'password_confirmation'=>(string)($p['password_confirmation']??''),'gender'=>trim((string)($p['gender']??'')),'student_personality'=>trim((string)($p['student_personality']??'')),'city_id'=>(int)($p['city_id']??0),'school_id'=>(int)($p['school_id']??0),'grade_id'=>(int)($p['grade_id']??0),'privacy_accept'=>$p['privacy_accept']??false,'device_name'=>api_device_name((string)($p['device_name']??'Android device'))];
 if ($v['full_name'] === '' || !preg_match('/^[a-zA-Z0-9_]{3,24}$/D', $v['username']) || strlen($v['password']) < 6 || strlen($v['password']) > 1024 || !hash_equals($v['password'], $v['password_confirmation']) || !in_array($v['gender'], ['male', 'female'], true) || !in_array($v['privacy_accept'], [true, 1, '1'], true) || ($v['email'] !== '' && filter_var($v['email'], FILTER_VALIDATE_EMAIL) === false) || ($v['student_personality'] !== '' && !in_array($v['student_personality'], ['explorer', 'achiever', 'calm'], true))) { api_registration_fail('validation_error', 'بيانات التسجيل غير مكتملة أو غير صالحة.', 422); }
 $s=$pdo->prepare('SELECT id,COALESCE(requires_school,1) requires_school FROM cities WHERE id=? AND is_active=1 LIMIT 1');$s->execute([$v['city_id']]);$city=$s->fetch(PDO::FETCH_ASSOC);if(!$city)api_registration_fail('city_unavailable','المدينة غير متاحة.',422);
 if((int)$city['requires_school']===1){$s=$pdo->prepare("SELECT id FROM schools WHERE id=? AND city_id=? AND is_active=1 AND COALESCE(student_visibility,'public')='public'");$s->execute([$v['school_id'],$v['city_id']]);if(!$s->fetchColumn())api_registration_fail('school_city_mismatch','المدرسة غير متاحة لهذه المدينة.',422);}else{$v['school_id']=null;}
 $s=$pdo->prepare("SELECT id FROM grades WHERE id=? AND is_active=1 AND COALESCE(student_visibility,'public')='public'");$s->execute([$v['grade_id']]);if(!$s->fetchColumn())api_registration_fail('grade_unavailable','الصف غير متاح.',422);return $v;
}
function api_idempotency_key(): string
{
    $key = trim((string) ($_SERVER['HTTP_IDEMPOTENCY_KEY'] ?? ''));
    if (!preg_match('/^[A-Za-z0-9._:-]{16,128}$/D', $key)) {
        api_registration_fail('idempotency_key_required', 'مفتاح منع التكرار مطلوب.', 400);
    }
    return $key;
}

/** Produce a stable, secret-keyed fingerprint without persisting request secrets. */
function api_registration_fingerprint(array $payload): string
{
    $normalized = [
        'full_name' => trim((string) ($payload['full_name'] ?? '')),
        'username' => trim((string) ($payload['username'] ?? '')),
        'phone' => trim((string) ($payload['phone'] ?? '')),
        'email' => strtolower(trim((string) ($payload['email'] ?? ''))),
        'password' => (string) ($payload['password'] ?? ''),
        'password_confirmation' => (string) ($payload['password_confirmation'] ?? ''),
        'gender' => trim((string) ($payload['gender'] ?? '')),
        'student_personality' => trim((string) ($payload['student_personality'] ?? '')),
        'city_id' => (int) ($payload['city_id'] ?? 0),
        'school_id' => (int) ($payload['school_id'] ?? 0),
        'grade_id' => (int) ($payload['grade_id'] ?? 0),
        'privacy_accept' => in_array($payload['privacy_accept'] ?? false, [true, 1, '1'], true),
        'device_name' => api_device_name((string) ($payload['device_name'] ?? 'Android device')),
    ];
    return hash_hmac('sha256', json_encode($normalized, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), api_server_secret());
}

function api_registration_encrypt(array $data): array
{
    $key = hash_hmac('sha256', 'registration-idempotency-v1', api_server_secret(), true);
    $nonce = random_bytes(12);
    $tag = '';
    $plaintext = json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    $ciphertext = openssl_encrypt($plaintext, 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $nonce, $tag, 'masary-registration-v1');
    if (!is_string($ciphertext)) { throw new RuntimeException('Unable to encrypt idempotency result.'); }
    return ['nonce' => base64_encode($nonce), 'tag' => base64_encode($tag), 'ciphertext' => base64_encode($ciphertext)];
}

function api_registration_decrypt(array $encrypted): array
{
    $key = hash_hmac('sha256', 'registration-idempotency-v1', api_server_secret(), true);
    $nonce = base64_decode((string) ($encrypted['nonce'] ?? ''), true);
    $tag = base64_decode((string) ($encrypted['tag'] ?? ''), true);
    $ciphertext = base64_decode((string) ($encrypted['ciphertext'] ?? ''), true);
    if (!is_string($nonce) || !is_string($tag) || !is_string($ciphertext)) { throw new RuntimeException('Invalid idempotency result.'); }
    $plaintext = openssl_decrypt($ciphertext, 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $nonce, $tag, 'masary-registration-v1');
    if (!is_string($plaintext)) { throw new RuntimeException('Unable to authenticate idempotency result.'); }
    $data = json_decode($plaintext, true, 32, JSON_THROW_ON_ERROR);
    if (!is_array($data)) { throw new RuntimeException('Invalid idempotency payload.'); }
    return $data;
}

function api_registration_idempotency(string $key, string $fingerprint, callable $operation): array
{
    $directory = IKHTABIRNI_DATA_DIR . '/api_registration_idempotency';
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        throw new RuntimeException('Unable to create idempotency directory.');
    }
    $path = $directory . '/' . hash('sha256', $key) . '.json';
    $handle = fopen($path, 'c+');
    if ($handle === false) {
        throw new RuntimeException('Unable to open idempotency record.');
    }
    @chmod($path, 0600);
    if (!flock($handle, LOCK_EX)) {
        fclose($handle);
        throw new RuntimeException('Unable to lock idempotency record.');
    }
    try {
        $raw = stream_get_contents($handle);
        $saved = is_string($raw) && $raw !== '' ? json_decode($raw, true) : null;
        if (is_array($saved) && (int) ($saved['expires_at'] ?? 0) > time()) {
            if (!hash_equals((string) ($saved['fingerprint'] ?? ''), $fingerprint)) {
                api_registration_fail('idempotency_key_conflict', 'استُخدم مفتاح الطلب مع بيانات تسجيل مختلفة.', 409);
            }
            return api_registration_decrypt((array) ($saved['encrypted_result'] ?? []));
        }
        $data = $operation();
        rewind($handle);
        ftruncate($handle, 0);
        fwrite($handle, json_encode([
            'expires_at' => time() + 86400,
            'fingerprint' => $fingerprint,
            'encrypted_result' => api_registration_encrypt($data),
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        fflush($handle);
        @chmod($path, 0600);
        return $data;
    } finally {
        flock($handle, LOCK_UN);
        fclose($handle);
    }
}

function api_register_student(PDO $pdo,array $p): array {
 $v=api_registration_validate($pdo,$p);$lock='masary_register_'.hash('sha256',strtolower($v['username']));$s=$pdo->prepare('SELECT GET_LOCK(?,10)');$s->execute([$lock]);if((int)$s->fetchColumn()!==1)api_registration_fail('registration_busy','حاول مرة أخرى.',503);
 try{$s=$pdo->prepare('SELECT id FROM app_users WHERE username=? LIMIT 1');$s->execute([$v['username']]);if($s->fetchColumn())api_registration_fail('username_taken','اسم المستخدم مستخدم مسبقًا.',409);$pdo->beginTransaction();do{$code=(string)random_int(100000,999999);$s=$pdo->prepare('SELECT id FROM app_users WHERE student_code=?');$s->execute([$code]);}while($s->fetchColumn());$s=$pdo->prepare("INSERT INTO app_users(role,full_name,username,phone,email,city_id,school_id,grade_id,gender,student_personality,password_hash,is_active,created_at,student_code) VALUES('student',?,?,?,?,?,?,?,?,?,?,1,?,?)");$s->execute([$v['full_name'],$v['username'],$v['phone']?:null,$v['email']?:null,$v['city_id'],$v['school_id'],$v['grade_id'],$v['gender'],$v['student_personality']?:null,password_hash($v['password'],PASSWORD_DEFAULT),api_mysql_datetime(time()),$code]);$id=(int)$pdo->lastInsertId();$pdo->commit();}catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();throw $e;}finally{$pdo->prepare('SELECT RELEASE_LOCK(?)')->execute([$lock]);}
 $user=['id'=>$id,'user_id'=>$id,'username'=>$v['username'],'full_name'=>$v['full_name'],'city_id'=>$v['city_id'],'school_id'=>$v['school_id'],'grade_id'=>$v['grade_id'],'student_code'=>$code,'avatar_path'=>null];$tokens=api_issue_session($pdo,$user,$v['device_name']);return ['access_token'=>$tokens['access_token'],'refresh_token'=>$tokens['refresh_token'],'expires_in'=>$tokens['expires_in'],'student'=>api_student_payload($user)];
}
