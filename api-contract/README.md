# عقد Masary API وبيئات Android

`openapi.json` هو المصدر المرجعي لعقد HTTP v1 المشترك بين تطبيق الطالب وحزمة PHP.
يصف الغلاف الموحد (`success` و`data` و`error` و`request_id`) ونقاط المصادقة والصفحة
الرئيسية فقط؛ ولا يشمل Push V2 أو PWA.

## البيئات

| القيمة | عنوان API | الاستخدام |
|---|---|---|
| `development` | قيمة محقونة؛ وإلا `https://development.masary.invalid/` | Debug افتراضيًا |
| `staging` | قيمة محقونة؛ وإلا `https://staging.masary.invalid/` | التحقق قبل الإصدار |
| `production` | `https://masary.app/` | Release افتراضيًا |

لا يفترض المشروع وجود نطاق تطوير أو staging ولا يحجز نطاقًا فرعيًا لـ
`masary.app`. تُحقن العناوين الحقيقية عبر Gradle properties أو متغيرات بيئة CI:

```bash
./gradlew :app-student:assembleDebug \
  -PmasaryDevelopmentBaseUrl=https://YOUR-DEVELOPMENT-HOST.example/
./gradlew :app-student:assembleDebug -PmasaryEnvironment=staging \
  -PmasaryStagingBaseUrl=https://YOUR-STAGING-HOST.example/
```

يقابل الخاصيتين متغيرا GitHub Actions
`MASARY_DEVELOPMENT_BASE_URL` و`MASARY_STAGING_BASE_URL`. عند غياب القيمة يستخدم
البناء نطاق `.invalid` المحجوز للاختبارات، فيفشل الاتصال بأمان. يجب أن يبدأ كل عنوان
بـ `https://` وينتهي بـ `/`. يرفض Gradle القيم والروابط غير الصالحة، ويرفض أي Debug
موجّه إلى production. لا يحتوي المستودع أسرارًا، ولا تنفذ فحوصات العقد اتصالات
شبكية أو عمليات على قاعدة بيانات.

## توافق مسارات API

العقد الحالي يصف المسارات المنشورة في المصدر تحت `/api/v1` فقط. المسار
`/api/android/v1` **غير موجود حاليًا**، ولا يضيف هذا التغيير endpoint أو rewrite
وهميًا له. إذا تقرر إدخاله لاحقًا، تكون خطة التوافق: إبقاء `/api/v1` دون كسر خلال
فترة انتقال معلنة، إضافة معالجات واختبارات عقد حقيقية للمسار الجديد، ترحيل Android
بعد تحقق staging، ثم إهمال القديم بإصدار مستقل ومقاييس استخدام وخطة rollback.

## التحقق المحلي

```bash
python3 scripts/validate-api-contract.py
find server-hostinger -type f -name '*.php' -print0 | xargs -0 -n1 php -l
./gradlew :core-network:testDebugUnitTest
```

عند تعديل استجابة PHP يجب تحديث OpenAPI والـ fixture المقابل وDTO Android في التغيير
نفسه. تتحقق أداة Python من المسارات والمعالجات والبيئات والأغلفة، وتثبت اختبارات JVM
أن Gson يستطيع تحويل أمثلة العقد إلى DTOs الفعلية.

## Android Notifications V1

يوثق العقد تسجيل التثبيت عبر `/api/v1/student/push-token`. هوية المستخدم من Bearer session فقط؛ لا يقبل body قيمة `user_id`. لا يعيد الخادم FCM token. راجع `docs/ISSUE_41_ANDROID_NOTIFICATIONS_INVENTORY.md` لتهيئة Firebase الآمنة وخطة الانتقال المستقبلية.
