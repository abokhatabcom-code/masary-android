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

## توقيع إصدارات Release

لا تُحفظ مفاتيح التوقيع أو كلمات المرور داخل المستودع. تُبنى الإصدارات الموقعة
يدويًا من تبويب **Actions** عبر تشغيل سير العمل `Android CI`، وتُنشأ ملفات المفاتيح
المؤقتة داخل `RUNNER_TEMP` ثم تُحذف عند انتهاء مهمة البناء.

يجب على مالك المستودع إضافة الأسرار التالية في **Settings → Secrets and variables → Actions**،
باستخدام القيم الحقيقية الخاصة بكل تطبيق:

| تطبيق الطالب | تطبيق الإدارة | تطبيق نقاط البيع |
| --- | --- | --- |
| `STUDENT_KEYSTORE_BASE64` | `ADMIN_KEYSTORE_BASE64` | `POS_KEYSTORE_BASE64` |
| `STUDENT_KEYSTORE_PASSWORD` | `ADMIN_KEYSTORE_PASSWORD` | `POS_KEYSTORE_PASSWORD` |
| `STUDENT_KEY_ALIAS` | `ADMIN_KEY_ALIAS` | `POS_KEY_ALIAS` |
| `STUDENT_KEY_PASSWORD` | `ADMIN_KEY_PASSWORD` | `POS_KEY_PASSWORD` |

يجب أن تكون قيمة كل متغير `*_KEYSTORE_BASE64` هي ترميز Base64 لملف keystore المقابل.
لا تسجل قيم الأسرار أو تنسخها إلى ملفات متتبعة بواسطة Git.
