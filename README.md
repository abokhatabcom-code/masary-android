# مساري التعليمية

مشروع أندرويد متعدد الوحدات يضم تطبيقات الطالب والإدارة ونقاط البيع، مبني بلغة Kotlin وواجهات Jetpack Compose وفق تصميم Material 3.

## المتطلبات

- JDK 17
- Android SDK 35
- لا يلزم تثبيت Gradle على الجهاز؛ جهّز Gradle Wrapper الموثوق عبر السكربت المرفق.

بعد استنساخ نسخة نظيفة، نزّل ملف Wrapper الرسمي وتحقق من سلامته، ثم افحص البيئة:

```bash
./scripts/bootstrap-gradle-wrapper.sh
java -version
./gradlew --version
```

إذا لم يكتشف Gradle حزمة Android تلقائيًا، عرّف `ANDROID_HOME` أو أضف مسار
الحزمة في ملف `local.properties` المحلي (هذا الملف مستثنى من Git).

## بوابة الجودة المحلية

شغّل اختبارات JVM لكل الوحدات وفحص Android Lint قبل إرسال أي تغيير:

```bash
./gradlew --no-daemon test lint
```

## بناء نسخة Debug

```bash
./gradlew --no-daemon \
  :app-student:assembleDebug \
  :app-admin:assembleDebug \
  :app-pos:assembleDebug
```

يرفع GitHub Actions ملفات البناء بثلاثة Artifacts مستقلة: `masary-student-debug-apk`،
و`masary-admin-debug-apk`، و`masary-pos-debug-apk`.

يجهّز CI ملف Wrapper الرسمي عبر السكربت نفسه ويتحقق من SHA-256 المثبّت قبل
استخدامه، ثم يتحقق من Wrapper ويشغّل جميع اختبارات JVM وAndroid Lint قبل بناء
التطبيقات الثلاثة. عند تغيير نسخة Gradle يجب تحديث نسخة التوزيعة ورابط JAR
والبصمة معًا ومراجعتها في تغيير مستقل.

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

يمكن لمسؤول المستودع إنشاء مواد التوقيع الأولية عبر سير العمل اليدوي المخصص لذلك.
