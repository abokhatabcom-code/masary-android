# مساري التعليمية

مشروع أندرويد متعدد الوحدات يضم تطبيقات الطالب والإدارة ونقاط البيع، مبني بلغة Kotlin وواجهات Jetpack Compose وفق تصميم Material 3.

## المتطلبات

- JDK 17
- Android SDK 35

## بناء نسخة Debug

```bash
./gradlew :app-student:assembleDebug
./gradlew :app-admin:assembleDebug
./gradlew :app-pos:assembleDebug
```

يرفع GitHub Actions ملفات البناء بثلاثة Artifacts مستقلة: `masary-student-debug-apk`،
و`masary-admin-debug-apk`، و`masary-pos-debug-apk`.

## توقيع نسخ Debug في GitHub Actions

لا تُحفظ مفاتيح التوقيع أو كلمات المرور داخل المستودع. عند التشغيل اليدوي لسير
العمل `Android CI`، يُستخدم keystore موحد لتوقيع نسخ Debug للتطبيقات الثلاثة.
يُنشأ الملف مؤقتًا داخل `RUNNER_TEMP` ثم يُحذف دائمًا عند انتهاء مهمة البناء.
أما البناء المحلي دون أسرار فيستمر باستخدام مفتاح Debug الافتراضي من Android.

يجب على مالك المستودع إضافة GitHub Actions Secrets التالية فقط في
**Settings → Secrets and variables → Actions**:

- `MASARY_KEYSTORE_BASE64`: ترميز Base64 لملف keystore الموحد.
- `MASARY_KEYSTORE_PASSWORD`: كلمة مرور ملف keystore.
- `MASARY_KEY_ALIAS`: الاسم المستعار للمفتاح.
- `MASARY_KEY_PASSWORD`: كلمة مرور المفتاح.

لا تسجل قيم الأسرار ولا تنسخها إلى ملفات متتبعة بواسطة Git.
