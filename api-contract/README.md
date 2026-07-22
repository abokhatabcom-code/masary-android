# عقد Masary API وبيئات Android

`openapi.json` هو المصدر المرجعي لعقد HTTP v1 المشترك بين تطبيق الطالب وحزمة PHP.
يصف الغلاف الموحد (`success` و`data` و`error` و`request_id`) ونقاط المصادقة والصفحة
الرئيسية فقط؛ ولا يشمل Push V2 أو PWA.

## البيئات

| القيمة | عنوان API | الاستخدام |
|---|---|---|
| `development` | `https://dev.masary.app/` | Debug افتراضيًا |
| `staging` | `https://staging.masary.app/` | التحقق قبل الإصدار |
| `production` | `https://masary.app/` | Release افتراضيًا |

يمكن اختيار بيئة غير الافتراضية وقت البناء فقط:

```bash
./gradlew :app-student:assembleDebug -PmasaryEnvironment=staging
./gradlew :app-student:assembleRelease -PmasaryEnvironment=staging
```

يرفض Gradle القيم غير المعروفة، كما يرفض توجيه Release إلى development. لا يحتوي
المستودع أسرارًا، ولا تنفذ فحوصات العقد اتصالات شبكية أو عمليات على قاعدة بيانات.

## التحقق المحلي

```bash
python3 scripts/validate-api-contract.py
find server-hostinger -type f -name '*.php' -print0 | xargs -0 -n1 php -l
./gradlew :core-network:testDebugUnitTest
```

عند تعديل استجابة PHP يجب تحديث OpenAPI والـ fixture المقابل وDTO Android في التغيير
نفسه. تتحقق أداة Python من المسارات والمعالجات والبيئات والأغلفة، وتثبت اختبارات JVM
أن Gson يستطيع تحويل أمثلة العقد إلى DTOs الفعلية.
