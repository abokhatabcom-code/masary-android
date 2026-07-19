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
