<?php
declare(strict_types=1);

const MASARY_REGISTRATION_TESTING = true;
$testDir = sys_get_temp_dir() . '/masary-registration-' . bin2hex(random_bytes(6));
define('IKHTABIRNI_DATA_DIR', $testDir);

function api_device_name(string $value): string { return trim($value) ?: 'Android device'; }
function api_server_secret(): string { return str_repeat('test-secret-', 5); }
function api_mysql_datetime(int $timestamp): string { return gmdate('Y-m-d H:i:s', $timestamp); }
function api_issue_session(PDO $pdo, array $user, string $device): array
{
    assert($pdo instanceof FixturePDO);
    if ($pdo->failSessionIssue) {
        throw new RuntimeException('Simulated isolated session failure.');
    }
    $pdo->insertSession((int) $user['id']);
    return ['access_token'=>'access-'.$user['id'],'refresh_token'=>'refresh-'.$user['id'],'expires_in'=>900];
}
function api_student_payload(array $user): array { return ['id'=>(string)$user['id'],'username'=>$user['username'],'display_name'=>$user['full_name']]; }
require dirname(__DIR__) . '/public_html/api/_registration.php';

final class FixturePDO extends PDO {
    public array $cities = [1 => ['id'=>1,'requires_school'=>1,'is_active'=>1], 2 => ['id'=>2,'requires_school'=>0,'is_active'=>1]];
    public array $schools = [10 => ['id'=>10,'city_id'=>1,'is_active'=>1,'public'=>1], 11 => ['id'=>11,'city_id'=>2,'is_active'=>1,'public'=>1], 12 => ['id'=>12,'city_id'=>1,'is_active'=>1,'public'=>0]];
    public array $grades = [7 => ['id'=>7,'is_active'=>1,'public'=>1], 8 => ['id'=>8,'is_active'=>1,'public'=>0]];
    public array $users = [];
    public array $sessions = [];
    public bool $failSessionIssue = false;
    private bool $transaction = false;
    private int $lastId = 0;
    private array $snapshot = [];
    public function __construct() {}
    public function prepare(string $query, array $options = []): PDOStatement|false { return new FixtureStatement($this, $query); }
    public function beginTransaction(): bool { $this->snapshot=[$this->users,$this->sessions,$this->lastId]; $this->transaction=true; return true; }
    public function commit(): bool { $this->snapshot=[]; $this->transaction=false; return true; }
    public function rollBack(): bool { [$this->users,$this->sessions,$this->lastId]=$this->snapshot; $this->snapshot=[]; $this->transaction=false; return true; }
    public function inTransaction(): bool { return $this->transaction; }
    public function lastInsertId(?string $name = null): string|false { return (string)$this->lastId; }
    public function insertUser(array $values): void { $this->lastId++; $this->users[$values[1]]=['id'=>$this->lastId,'student_code'=>$values[11]]; }
    public function insertSession(int $studentId): void { $this->sessions[]=['student_id'=>$studentId]; }
}

final class FixtureStatement extends PDOStatement {
    private mixed $result = false;
    public function __construct(private FixturePDO $db, private string $sql) {}
    public function execute(?array $params = null): bool {
        $p=$params??[];
        if (str_contains($this->sql,'FROM cities WHERE id=')) { $c=$this->db->cities[(int)($p[0]??0)]??null; $this->result=$c&&$c['is_active']?['id'=>$c['id'],'requires_school'=>$c['requires_school']]:false; }
        elseif (str_contains($this->sql,'FROM schools WHERE id=')) { $s=$this->db->schools[(int)($p[0]??0)]??null; $this->result=$s&&$s['city_id']==(int)($p[1]??0)&&$s['is_active']&&$s['public']?$s['id']:false; }
        elseif (str_contains($this->sql,'FROM grades WHERE id=')) { $g=$this->db->grades[(int)($p[0]??0)]??null; $this->result=$g&&$g['is_active']&&$g['public']?$g['id']:false; }
        elseif (str_contains($this->sql,'GET_LOCK')) { $this->result=1; }
        elseif (str_contains($this->sql,'RELEASE_LOCK')) { $this->result=1; }
        elseif (str_contains($this->sql,'FROM app_users WHERE username=')) { $this->result=$this->db->users[(string)($p[0]??'')]['id']??false; }
        elseif (str_contains($this->sql,'FROM app_users WHERE student_code=')) { $code=(string)($p[0]??''); $this->result=false; foreach($this->db->users as $u){if($u['student_code']===$code)$this->result=$u['id'];} }
        elseif (str_starts_with($this->sql,'INSERT INTO app_users')) { $this->db->insertUser($p); $this->result=true; }
        else { throw new RuntimeException('Unhandled fixture SQL: '.$this->sql); }
        return true;
    }
    public function fetch(int $mode = PDO::FETCH_DEFAULT, int $cursorOrientation = PDO::FETCH_ORI_NEXT, int $cursorOffset = 0): mixed { return is_array($this->result)?$this->result:false; }
    public function fetchColumn(int $column = 0): mixed { return is_array($this->result)?reset($this->result):$this->result; }
}

function payload(array $changes=[]): array { return array_replace(['full_name'=>'طالب تجريبي','username'=>'student_1','phone'=>'','email'=>'student@example.com','password'=>'secret1','password_confirmation'=>'secret1','gender'=>'male','student_personality'=>'calm','city_id'=>1,'school_id'=>10,'grade_id'=>7,'privacy_accept'=>true,'device_name'=>'test'], $changes); }
function expectError(string $code, callable $call): void { try{$call();}catch(RegistrationApiException $e){if($e->errorCode===$code)return; throw new RuntimeException("Expected $code, got {$e->errorCode}");} throw new RuntimeException("Expected $code"); }
function check(bool $condition,string $message): void { if(!$condition)throw new RuntimeException($message); }

$db=new FixturePDO();
expectError('city_unavailable',fn()=>api_registration_validate($db,payload(['city_id'=>99])));
expectError('grade_unavailable',fn()=>api_registration_validate($db,payload(['grade_id'=>99])));
expectError('school_city_mismatch',fn()=>api_registration_validate($db,payload(['school_id'=>11])));
expectError('school_city_mismatch',fn()=>api_registration_validate($db,payload(['school_id'=>12])));
expectError('grade_unavailable',fn()=>api_registration_validate($db,payload(['grade_id'=>8])));
$db->users['taken']=['id'=>50,'student_code'=>'111111'];
expectError('username_taken',fn()=>api_register_student($db,payload(['username'=>'taken'])));

$failingDb=new FixturePDO();
$failingDb->failSessionIssue=true;
try { api_register_student($failingDb,payload(['username'=>'retry_student'])); throw new RuntimeException('Expected session failure.'); }
catch (RuntimeException $exception) { check($exception->getMessage()==='Simulated isolated session failure.','Unexpected failure.'); }
check($failingDb->users===[],'Student remained after session rollback.');
check($failingDb->sessions===[],'Session remained after rollback.');
$failingDb->failSessionIssue=false;
$retried=api_register_student($failingDb,payload(['username'=>'retry_student']));
check($retried['student']['username']==='retry_student','Retry failed after transactional rollback.');
check(count($failingDb->users)===1&&count($failingDb->sessions)===1,'Retry did not create exactly one student and session.');

$request=payload(); $fingerprint=api_registration_fingerprint($request); $calls=0;
$first=api_registration_idempotency('behavior-key-0001',$fingerprint,function()use($db,$request,&$calls){$calls++;return api_register_student($db,$request);});
$second=api_registration_idempotency('behavior-key-0001',$fingerprint,function()use(&$calls){$calls++;throw new RuntimeException('must not execute');});
check($first===$second,'Same request must return the previous result.');
check($calls===1,'Same request created a second account.');
check(count($db->users)===2,'Unexpected number of fixture users.');
check(count($db->sessions)===1,'Replay created a second session.');
expectError('idempotency_key_conflict',fn()=>api_registration_idempotency('behavior-key-0001',api_registration_fingerprint(payload(['email'=>'different@example.com'])),fn()=>[]));
$file=$testDir.'/api_registration_idempotency/'.hash('sha256','behavior-key-0001').'.json'; $raw=(string)file_get_contents($file);
check(!str_contains($raw,'access-')&&!str_contains($raw,'refresh-')&&!str_contains($raw,'secret1'),'Idempotency file contains raw secrets.');
check((fileperms($file)&0777)===0600,'Idempotency file permissions are not 0600.');
echo "Behavioral registration tests passed.\n";
