# Issue 41 — Android Notifications V1 inventory

## الجرد قبل التنفيذ

- كان Gradle يخلو من Firebase وWorkManager، ولم توجد وحدة للإشعارات.
- كان Manifest الطالب يطلب الإنترنت فقط، ولم توجد قنوات أو خدمة FCM أو إذن Android 13.
- كانت حزمة Hostinger وعقد OpenAPI يقدمان مصادقة الطالب والصفحة الرئيسية فقط، دون سجل تثبيت Android.
- عدّاد الإشعارات الموجود في Home قراءة من `app_notifications` وليس نظام تسجيل أجهزة أو إرسال حملات.

## Firebase خارج Git والبيئات

لا يستخدم المشروع `google-services.json` ولا service account. تُحقن القيم العامة المطلوبة لتهيئة عميل Firebase من Gradle properties لكل بيئة:

- `masaryFirebaseDevelopmentProjectId`, `masaryFirebaseDevelopmentApplicationId`, `masaryFirebaseDevelopmentApiKey`
- النظائر `Staging` و`Production`.

يجب تخزينها في CI variables المحمية، وليس ملفات Git. يجب أن تكون المجموعة كاملة أو غائبة. Debug محصور في development/staging ويرفض production. عند غياب المجموعة يعيد `FirebaseInitializer` حالة disabled ولا يبدأ FCM ولا يتعطل التطبيق. مفاتيح خادم FCM وservice accounts ليست ضمن تطبيق Android إطلاقًا.

## دورة الحياة والأمان

يولد التطبيق UUID عشوائيًا ثابتًا لكل تثبيت ولا يستخدم Android ID أو IMEI أو FCM token كهوية. يحفظ FCM token المعلّق فقط بتشفير AES-GCM ومفتاح Android Keystore، ثم يزامنه WorkManager بعد وجود جلسة مع exponential backoff. لا تحجب المزامنة Home. عند logout تحفظ صلاحية الفصل مؤقتًا بصورة مشفرة، تفصل الحالة المحلية فورًا، وتطلب تعطيل التثبيت في الخلفية.

على الخادم لا يُخزن FCM token خامًا: AES-256-GCM بمفتاح مشتق من `api_server_secret()` الموجود خارج Git، مع envelope ذي version byte. لتدوير المفتاح: أضف نسخة مفتاح جديدة، فك السجلات بالنسخة القديمة وأعد تشفيرها بالجديدة في مهمة صيانة مراجعة، ثم اسحب القديم بعد التحقق؛ لا تسجل plaintext أثناء العملية.

## الفصل عن أنظمة الويب

هذا التنفيذ خاص بـ Android Notifications V1 ولا يستدعي أو يعدل Push V2 أو PWA أو service worker أو جداول اشتراكات المتصفح. لا يحتوي على محرك حملات أو إرسال جماعي ولا ترسل اختبارات المستودع رسائل فعلية.

## انتقال API مستقبلًا

يبقى المسار الحقيقي `/api/v1/student/push-token`. الانتقال المستقبلي إلى `/api/android/v1` يحتاج handlers واختبارات staging حقيقية أولًا، ثم فترة توافق مزدوجة ومراقبة الاستخدام، ثم ترحيل العميل وإهمال v1 بخطة rollback. لا ينشئ هذا التغيير endpoint وهميًا.
