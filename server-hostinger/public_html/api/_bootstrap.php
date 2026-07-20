<?php
declare(strict_types=1);

if (PHP_VERSION_ID < 80100) {
    http_response_code(500);
    header('Content-Type: application/json; charset=utf-8');
    echo '{"success":false,"data":null,"error":{"code":"server_configuration","message":"إعداد الخادم غير متوافق."}}';
    exit;
}

const MASARY_API_VERSION = '1.0.0';
const MASARY_ACCESS_TTL_SECONDS = 900;
const MASARY_REFRESH_TTL_SECONDS = 2592000;
const MASARY_MAX_ACTIVE_SESSIONS = 5;
const MASARY_MAX_JSON_BYTES = 32768;

$publicRoot = dirname(__DIR__);
if (!defined('IKHTABIRNI_PUBLIC_ROOT')) {
    define('IKHTABIRNI_PUBLIC_ROOT', $publicRoot);
}
if (!defined('IKHTABIRNI_DATA_DIR')) {
    define('IKHTABIRNI_DATA_DIR', dirname($publicRoot) . DIRECTORY_SEPARATOR . '_ikhtabirni_data');
}

require_once IKHTABIRNI_PUBLIC_ROOT . '/admin/_db.php';
require_once IKHTABIRNI_PUBLIC_ROOT . '/includes/security_cleanup.php';

function api_request_id(): string
{
    static $id = null;
    if (is_string($id)) {
        return $id;
    }
    $incoming = trim((string)($_SERVER['HTTP_X_REQUEST_ID'] ?? ''));
    if ($incoming !== '' && preg_match('/^[A-Za-z0-9._-]{8,80}$/', $incoming)) {
        $id = $incoming;
    } else {
        $id = bin2hex(random_bytes(12));
    }
    return $id;
}

function api_is_https(): bool
{
    if (!empty($_SERVER['HTTPS']) && strtolower((string)$_SERVER['HTTPS']) !== 'off') {
        return true;
    }
    if ((int)($_SERVER['SERVER_PORT'] ?? 0) === 443) {
        return true;
    }
    return strtolower(trim((string)($_SERVER['REQUEST_SCHEME'] ?? ''))) === 'https';
}

function api_send_security_headers(): void
{
    if (headers_sent()) {
        return;
    }
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
    header('Pragma: no-cache');
    header('Expires: 0');
    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: DENY');
    header('Referrer-Policy: no-referrer');
    header('Permissions-Policy: geolocation=(), microphone=(), camera=()');
    header('X-Request-ID: ' . api_request_id());
    if (api_is_https()) {
        header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
    }
}

function api_response(bool $success, mixed $data = null, ?array $error = null, int $status = 200): never
{
    api_send_security_headers();
    http_response_code($status);
    echo json_encode([
        'success' => $success,
        'data' => $data,
        'error' => $error,
        'request_id' => api_request_id(),
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}

function api_error(string $code, string $message, int $status = 400): never
{
    api_response(false, null, ['code' => $code, 'message' => $message], $status);
}

function api_require_https(): void
{
    if (PHP_SAPI === 'cli' || api_is_https()) {
        return;
    }
    api_error('https_required', 'الاتصال الآمن مطلوب.', 426);
}

function api_require_method(string ...$allowed): void
{
    $method = strtoupper((string)($_SERVER['REQUEST_METHOD'] ?? 'GET'));
    $allowed = array_values(array_unique(array_map('strtoupper', $allowed)));
    if (!in_array($method, $allowed, true)) {
        if (!headers_sent()) {
            header('Allow: ' . implode(', ', $allowed));
        }
        api_error('method_not_allowed', 'طريقة الطلب غير مسموحة.', 405);
    }
}

function api_read_json(): array
{
    $contentType = strtolower(trim((string)($_SERVER['CONTENT_TYPE'] ?? '')));
    if ($contentType !== '' && !str_starts_with($contentType, 'application/json')) {
        api_error('unsupported_media_type', 'يجب إرسال البيانات بصيغة JSON.', 415);
    }

    $contentLength = (int)($_SERVER['CONTENT_LENGTH'] ?? 0);
    if ($contentLength > MASARY_MAX_JSON_BYTES) {
        api_error('payload_too_large', 'حجم الطلب أكبر من المسموح.', 413);
    }

    $raw = file_get_contents('php://input', false, null, 0, MASARY_MAX_JSON_BYTES + 1);
    if (!is_string($raw)) {
        api_error('invalid_json', 'تعذر قراءة الطلب.', 400);
    }
    if (strlen($raw) > MASARY_MAX_JSON_BYTES) {
        api_error('payload_too_large', 'حجم الطلب أكبر من المسموح.', 413);
    }
    if (trim($raw) === '') {
        return [];
    }

    try {
        $decoded = json_decode($raw, true, 32, JSON_THROW_ON_ERROR);
    } catch (JsonException) {
        api_error('invalid_json', 'صيغة JSON غير صحيحة.', 400);
    }
    if (!is_array($decoded)) {
        api_error('invalid_json', 'يجب أن يكون الطلب كائن JSON.', 400);
    }
    return $decoded;
}

function api_string(array $payload, string $key, int $maxLength, bool $required = true): string
{
    $value = trim((string)($payload[$key] ?? ''));
    if ($required && $value === '') {
        api_error('validation_error', 'بعض الحقول المطلوبة غير مكتملة.', 422);
    }
    $length = function_exists('mb_strlen') ? mb_strlen($value, 'UTF-8') : strlen($value);
    if ($length > $maxLength) {
        api_error('validation_error', 'إحدى القيم أطول من المسموح.', 422);
    }
    return $value;
}

function api_client_ip(): string
{
    return substr((string)($_SERVER['REMOTE_ADDR'] ?? 'unknown'), 0, 64);
}

function api_user_agent(): string
{
    return substr((string)($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 255);
}

function api_device_name(string $value): string
{
    $value = preg_replace('/[\x00-\x1F\x7F]/u', '', trim($value)) ?? '';
    if ($value === '') {
        return 'Android device';
    }
    return function_exists('mb_substr') ? mb_substr($value, 0, 120, 'UTF-8') : substr($value, 0, 120);
}

function api_bearer_token(): string
{
    $header = trim((string)($_SERVER['HTTP_AUTHORIZATION'] ?? ''));
    if ($header === '' && function_exists('apache_request_headers')) {
        $headers = apache_request_headers();
        $header = trim((string)($headers['Authorization'] ?? $headers['authorization'] ?? ''));
    }
    if (!preg_match('/^Bearer\s+([^\s]+)$/i', $header, $match)) {
        return '';
    }
    return (string)$match[1];
}

function api_maintenance_guard(): void
{
    $maintenanceFile = IKHTABIRNI_DATA_DIR . '/MAINTENANCE_ON';
    if (!is_file($maintenanceFile)) {
        return;
    }
    $message = trim((string)@file_get_contents($maintenanceFile));
    if ($message === '') {
        $message = 'المنصة تحت الصيانة الآن. حاول لاحقًا.';
    }
    if (!headers_sent()) {
        header('Retry-After: 120');
    }
    api_error('maintenance', $message, 503);
}

function api_rate_limit_dir(): string
{
    $dir = IKHTABIRNI_DATA_DIR . '/api_rate_limits';
    if (!is_dir($dir) && !@mkdir($dir, 0770, true) && !is_dir($dir)) {
        throw new RuntimeException('Unable to create rate limit directory.');
    }
    return $dir;
}

function api_rate_limit(string $scope, string $identity, int $limit, int $windowSeconds): void
{
    $now = time();
    $bucket = hash('sha256', strtolower($scope) . '|' . strtolower($identity) . '|' . api_client_ip());
    $file = api_rate_limit_dir() . '/' . $bucket . '.json';
    $handle = @fopen($file, 'c+');
    if ($handle === false) {
        throw new RuntimeException('Unable to open rate limit file.');
    }

    $limited = false;
    try {
        if (!flock($handle, LOCK_EX)) {
            throw new RuntimeException('Unable to lock rate limit file.');
        }
        $raw = stream_get_contents($handle);
        $decoded = is_string($raw) && $raw !== '' ? json_decode($raw, true) : [];
        $attempts = is_array($decoded['attempts'] ?? null) ? $decoded['attempts'] : [];
        $cutoff = $now - max(60, $windowSeconds);
        $attempts = array_values(array_filter(array_map('intval', $attempts), static fn(int $ts): bool => $ts >= $cutoff));
        $limited = count($attempts) >= $limit;
        if (!$limited) {
            $attempts[] = $now;
            rewind($handle);
            ftruncate($handle, 0);
            fwrite($handle, json_encode(['attempts' => $attempts], JSON_UNESCAPED_SLASHES));
            fflush($handle);
        }
    } finally {
        @flock($handle, LOCK_UN);
        fclose($handle);
    }

    if ($limited) {
        if (!headers_sent()) {
            header('Retry-After: ' . max(60, $windowSeconds));
        }
        api_error('rate_limited', 'محاولات كثيرة خلال وقت قصير. انتظر قليلًا ثم حاول مرة أخرى.', 429);
    }
}

function api_exception_handler(Throwable $exception): never
{
    error_log('Masary API failure [' . api_request_id() . '] ' . get_class($exception));
    api_error('server_error', 'حدث خطأ مؤقت. حاول مرة أخرى.', 500);
}

set_exception_handler('api_exception_handler');
api_send_security_headers();
api_require_https();
api_maintenance_guard();
