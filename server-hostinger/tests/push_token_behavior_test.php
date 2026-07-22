<?php
declare(strict_types=1);
final class ApiTestError extends RuntimeException { function __construct(public string $apiCode){parent::__construct($apiCode);} }
function api_error(string $code,string $message,int $status=400):never{throw new ApiTestError($code);} function api_mysql_datetime(int $t):string{return gmdate('Y-m-d H:i:s',$t);} function api_server_secret():string{return str_repeat('isolated-test-secret-',3);}
require dirname(__DIR__).'/public_html/api/_push_tokens.php';
final class PushPDO extends PDO {public array $calls=[];function __construct(){}function prepare(string $q,array $o=[]):PDOStatement|false{return new PushStatement($this,$q);}}
final class PushStatement extends PDOStatement {function __construct(private PushPDO $db,private string $sql){}function execute(?array $p=null):bool{$this->db->calls[]=[$this->sql,$p];return true;}}
function check(bool $ok,string $m):void{if(!$ok)throw new RuntimeException($m);} function payload(array $c=[]):array{return array_replace(['installation_id'=>'123e4567-e89b-42d3-a456-426614174000','fcm_token'=>str_repeat('safe-token-',8),'app_version'=>'1.0','app_build'=>1,'platform'=>'android','locale'=>'ar-YE','timezone'=>'Asia/Aden','permission_status'=>'Granted'],$c);}
$db=new PushPDO(); api_upsert_android_installation($db,42,payload()); api_upsert_android_installation($db,42,payload(['fcm_token'=>str_repeat('rotated-',8)]));
check(count($db->calls)===4,'upsert not executed'); check(str_contains($db->calls[1][0],'ON DUPLICATE KEY UPDATE'),'not idempotent'); check($db->calls[1][1][1]===42,'session user not bound'); check($db->calls[1][1][2]!==payload()['fcm_token'],'raw token stored'); check(!in_array(payload()['fcm_token'],$db->calls[1][1],true),'token leaked to SQL params'); check($db->calls[1][1][3]===hash('sha256',payload()['fcm_token']),'hash mismatch'); check($db->calls[1][1][0]===$db->calls[3][1][0],'rotation changed installation');
api_disable_android_installation($db,42,payload(['fcm_token'=>''])); check($db->calls[4][1][4]===payload()['installation_id'],'detach not installation-scoped'); check($db->calls[4][1][5]===42,'detach not session-scoped');
try{api_android_installation_payload(payload(['installation_id'=>'Android-ID']),true);throw new RuntimeException('sensitive id accepted');}catch(ApiTestError $e){check($e->apiCode==='invalid_installation','wrong validation');}
echo "Push installation behavioral tests passed.\n";
