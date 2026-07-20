# Masary Hostinger API — Phase 1

حزمة API أصلية لتطبيقات مساري، تبدأ بمصادقة تطبيق الطالب دون WebView أو جلسات متصفح.

## الاعتماد على المنصة الحالية

ترفع محتويات `server-hostinger/public_html/api/` إلى:

```text
public_html/api/
```

الحزمة تعيد استخدام الملفات الموجودة أصلًا في منصة مساري:

```text
public_html/admin/_db.php
public_html/includes/security_cleanup.php
```

ولا تحتوي أي بيانات اتصال بقاعدة البيانات أو كلمات مرور داخل Git.

## المتطلبات

- PHP 8.1 أو أحدث.
- Apache مع `mod_rewrite` (متوفر عادةً على Hostinger).
- HTTPS مفعل على `masary.app`.
- قاعدة منصة مساري الحالية وجدول `app_users`.
- صلاحية كتابة للمجلد الخاص خارج `public_html`:

```text
_ikhtabirni_data/
```

ينشئ API تلقائيًا مفتاح HMAC سريًا داخل:

```text
_ikhtabirni_data/api/token_pepper.key
```

لا تنقل هذا الملف إلى `public_html` ولا تضفه إلى Git.

## نقاط API

```text
GET  /api/v1/health
POST /api/v1/auth/student/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/me
```

## اختبار الصحة

```bash
curl -sS https://masary.app/api/v1/health
```

## تسجيل الدخول

```bash
curl -sS https://masary.app/api/v1/auth/student/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"STUDENT_USERNAME","password":"STUDENT_PASSWORD","device_name":"Android test"}'
```

لا تضع كلمة مرور حقيقية في سجل عام أو GitHub Issue.

## الأمان

- رموز opaque عشوائية؛ لا JWT.
- تخزين HMAC hash فقط للرموز.
- Access Token لمدة 15 دقيقة.
- Refresh Token لمدة 30 يومًا مع rotation وكشف إعادة الاستخدام.
- حد أقصى 5 جلسات نشطة لكل طالب.
- Rate limiting حسب IP واسم المستخدم.
- لا CORS مفتوح.
- لا تعديل على Push V2 أو PWA.

## النشر الآمن

1. خذ نسخة احتياطية من المشروع وقاعدة البيانات.
2. ارفع مجلد `api` فقط إلى `public_html/api`.
3. تأكد أن ملف `public_html/api/.htaccess` رُفع ولم يتغير اسمه.
4. افتح `/api/v1/health` وتأكد من `success: true`.
5. جرّب تسجيل الدخول بحساب طالب تجريبي فقط.
6. لا تحذف ملفات المنصة الحالية ولا تستبدل `admin/_db.php`.

## التوافق مع Android

استجابة تسجيل الدخول متوافقة مع `StudentLoginResponseDto` الحالي في تطبيق الطالب:

```text
POST /api/v1/auth/student/login
```

المرحلة التالية تربط نسخة Android release بعنوان `https://masary.app/` وتضيف refresh/logout إلى `core-network` و`core-security`.
