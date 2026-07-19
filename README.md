# مساري التعليمية

تطبيق أندرويد تعليمي مبني بلغة Kotlin وواجهات Jetpack Compose وفق تصميم Material 3.

## المتطلبات

- JDK 17
- Android SDK 35

## بناء نسخة Debug

```bash
./gradlew assembleDebug
```

ينتج ملف APK في `app/build/outputs/apk/debug/app-debug.apk`. كما ينفذ GitHub Actions
البناء تلقائيًا ويرفع الملف باسم `masary-educational-debug-apk` ضمن Artifacts.
