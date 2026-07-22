# Masary Hostinger API

حزمة API أصلية لتطبيقات مساري، تبدأ بمصادقة تطبيق الطالب وتعرض بيانات صفحته الرئيسية دون WebView أو جلسات متصفح.

## الاعتماد على المنصة الحالية

ترفع محتويات `server-hostinger/public_html/api/` إلى:

```text
public_html/api/
```

الحزمة تعيد استخدام الملفات الموجودة أصلًا في منصة مساري:

```text
public_html/admin/_db.php
public_html/includes/security_cleanup.php
public_html/includes/student_dashboard_optimization.php
public_html/includes/student_study_guide.php
```

ولا تحتوي أي بيانات اتصال بقاعدة البيانات أو كلمات مرور داخل Git.

## المتطلبات

- PHP 8.1 أو أحدث.
- Apache مع `mod_rewrite`.
- HTTPS مفعل على `masary.app`.
- قاعدة منصة مساري الحالية وجدول `app_users`.
- ملفات الموجّه الدراسي الحالية في `public_html/includes`.
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
GET  /api/v1/student/home
PUT  /api/v1/student/push-token
DELETE /api/v1/student/push-token
```

## الصفحة الرئيسية للطالب

المسار:

```text
GET /api/v1/student/home
Authorization: Bearer ACCESS_TOKEN
```

يعيد طلبًا واحدًا مجمّعًا يحتوي على:

- الجواهر والنقاط والمستوى.
- سلسلة الإنجاز والهدف والحمايات.
- نشاط اليوم الحقيقي.
- حالة الاشتراك.
- عدد الإشعارات غير المقروءة.
- آخر مادة ووحدة وصل إليهما الطالب.
- الموجّه الدراسي الحالي وخطواته مرتبة كما أنشأها النظام القديم.

هذا المسار **قراءة فقط**، ولا ينشئ خطة يومية ولا يعيد توليد الموجّه عند فتح الصفحة. التوليد المجدول للموجّه يبقى مستقلًا، ولا يتم تعديل Push V2 الخاص بالمتصفحات.

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

لا تضع كلمة مرور أو Access Token حقيقيًا في سجل عام أو GitHub Issue.

## الأمان

- رموز opaque عشوائية؛ لا JWT.
- تخزين HMAC hash فقط للرموز.
- Access Token لمدة 15 دقيقة.
- Refresh Token لمدة 30 يومًا مع rotation وكشف إعادة الاستخدام.
- حد أقصى 5 جلسات نشطة لكل طالب.
- Rate limiting حسب IP واسم المستخدم.
- الصفحة الرئيسية لا تستقبل `student_id` من التطبيق؛ هوية الطالب تأتي من الرمز فقط.
- لا CORS مفتوح.
- لا تعديل على Push V2 أو PWA.
- مسار Push Token خاص بـ Android Notifications V1 فقط؛ لا يرسل إشعارات ولا ينشئ حملات.

## الأداء والضغط العالي

- طلب واحد مجمّع للصفحة الرئيسية بدل عدة طلبات.
- كل الاستعلامات مقيّدة بالطالب المسجّل.
- لا توجد عملية توليد للموجّه أثناء فتح الصفحة.
- تعتمد القراءة على الفهارس الحالية مثل الطالب/التاريخ والإشعارات غير المقروءة.
- لا توجد حلقات تمر على الطلاب داخل طلب الصفحة الرئيسية.

## النشر الآمن

1. خذ نسخة احتياطية من المشروع وقاعدة البيانات.
2. ارفع مجلد `api` فقط إلى `public_html/api` واستبدل ملفات API القديمة بالجديدة.
3. لا تستبدل `public_html` كاملًا.
4. تأكد أن ملف `public_html/api/.htaccess` رُفع ولم يتغير اسمه.
5. افتح `/api/v1/health` وتأكد من `success: true`.
6. جرّب تسجيل الدخول بحساب طالب اختباري.
7. اختبر `/api/v1/student/home` باستخدام Access Token للحساب الاختباري.
8. لا تحذف ملفات المنصة الحالية ولا تستبدل `admin/_db.php` أو ملفات الموجّه.

## التوافق مع Android

تطبيق الطالب يستخدم:

```text
POST /api/v1/auth/student/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/me
GET  /api/v1/student/home
```

تظل خوارزمية اختيار مواد وخطوات الموجّه على الخادم، بينما يعرض تطبيق Android النتائج فقط.

## Native student registration

The public registration adapter exposes active cities, public active grades, city-scoped public active schools, and `POST /api/v1/auth/student/register`. Registration requires a 16–128 character `Idempotency-Key`, binds it to a normalized request fingerprint, validates every academic relationship on the server, serializes username creation with a MySQL advisory lock, and immediately issues the existing access/refresh session pair. Idempotency results are authenticated-encrypted with an AES-256-GCM key derived from the server secret; raw passwords and tokens are never written to the JSON record. The adapter never accepts curriculum, section, or branch input; curriculum resolution remains the legacy school → city → active `منهج عدن` rule. Password recovery remains a manual-support path; no OTP endpoint exists. Do not run SQL or deploy these files as part of repository validation.
