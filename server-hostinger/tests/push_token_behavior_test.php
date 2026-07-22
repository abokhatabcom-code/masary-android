<?php
declare(strict_types=1);
final class ApiTestError extends RuntimeException { public function __construct(public string $apiCode){parent::__construct($apiCode);} }
function api_error(string $code,string $message,int $status=400): never { throw new ApiTestError($code); }
function api_mysql_datetime(int $timestamp): string { return gmdate('Y-m-d H:i:s',$timestamp); }
require dirname(__DIR__).'/public_html/api/_push_tokens.php';
final class PushPDO extends PDO { public array $calls=[]; public function __construct(){} public function prepare(string $query,array $options=[]): PDOStatement|false{return new PushStatement($this,$query);} }
final class PushStatement extends PDOStatement { public function __construct(private PushPDO $db,private string $sql){} public function execute(?array $params=null):bool{$this->db->calls[]=[$this->sql,$params];return true;} }
function check(bool $ok,string $message):void{if(!$ok)throw new RuntimeException($message);}
function invalid(array $payload):void{try{api_validate_android_push_token($payload);}catch(ApiTestError $e){check($e->apiCode==='invalid_push_token','wrong error');return;}throw new RuntimeException('invalid token accepted');}
invalid(['token'=>'short']); invalid(['token'=>str_repeat('a',32),'platform'=>'web']); invalid(['token'=>str_repeat('a',32),'app'=>'admin']); invalid(['token'=>str_repeat('a',31).' ']);
$db=new PushPDO(); $token=str_repeat('safe-token-',8); api_upsert_android_push_token($db,42,['token'=>$token,'platform'=>'android','app'=>'student']);
check(count($db->calls)===1,'upsert not executed'); check($db->calls[0][1][0]===42,'authenticated user not bound'); check($db->calls[0][1][2]===hash('sha256',$token),'hash mismatch');
api_delete_android_push_token($db,42,['token'=>$token]); check($db->calls[1][1][1]===42,'delete user not scoped'); check(!in_array($token,$db->calls[1][1],true),'delete retained raw token');
echo "Push token behavioral tests passed.\n";
