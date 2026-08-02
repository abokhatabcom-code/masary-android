# جرد المرحلة العاشرة — صفحة المادة الدراسية

**المهمة:** GitHub Issue #49  
**فرع التنفيذ:** `phase-10-subject-page-implementation`  
**نقطة الأساس:** `aefbd7efdaf42e13387d63b0056f55f40ba00410`  
**تاريخ الجرد:** 2026-08-02

## الملخص التنفيذي

أصبحت قائمة المواد مستقلة في المرحلة التاسعة، لكن وجهة `SubjectDetails(subjectVersionId)` ما تزال شاشة مؤقتة داخل `feature-home` وتعتمد على Home Snapshot لاستخراج الاسم فقط. لا توجد API مستقلة لتفاصيل مادة، ولا Repository أو ViewModel أو Cache أو واجهة مملوكة لصفحة المادة.

البيانات الحالية تسمح ببناء صفحة مادة حقيقية وآمنة من دون ترحيل قاعدة البيانات: هوية المادة ونسخة المنهج ونمط المحتوى من `subjects/subject_versions`، ونقاط المادة والقلوب من `student_subject_state`، والأجزاء من `units/lessons`، وآخر نشاط من `student_last_activity`. في المقابل، مستوى المادة وصيغة شريط التقدم ووقت الاستعادة التالي ومفتاح الوسائط وحالة الوصول التفصيلية ليست كلها مثبتة بمصدر واحد موثوق في مسار Android الحالي؛ لذلك يجب أن تعاد بحالات صريحة ولا تُخترع.

## 1. الوضع الحالي في Android

### 1.1 عقد التنقل

الملف:

`feature-home/src/main/java/app/masary/feature/home/ui/StudentDestinations.kt`

العقد الموجود:

```kotlin
StudentDestination.SubjectDetails(
    subjectVersionId: Int,
    unitId: Int? = null,
    actionKey: String? = null,
)
```

المعرف الموجب هو المفتاح الصحيح لصفحة المادة. حقلا `unitId/actionKey` تاريخيان لمسارات أخرى ولا ينبغي أن يتحولا إلى مصدر بيانات للصفحة.

### 1.2 الوجهة المؤقتة

الملف:

`feature-home/src/main/java/app/masary/feature/home/ui/StudentHomePreparationRoute.kt`

السلوك الحالي:

- يبحث عن المادة داخل `Home Snapshot`.
- يعرض الاسم ورسالة أن الوحدات والدروس ومركز التدريب ستأتي لاحقًا.
- لا يتحقق خادميًا أن المادة ما تزال مرتبطة بالطالب.
- لا يملك تحميلًا أو Cache أو Offline مستقلًا.

### 1.3 قائمة المواد

الوحدة `feature-subjects` مملوكة لقائمة المواد الكاملة، وتوفر:

- `StudentSubjectsApi`.
- Repository مع refresh token والتحقق من `student_id`.
- DataStore Cache مملوك للطالب.
- ViewModel وواجهة RTL.

قرار الملكية: لا تُحمّل هذه الوحدة تفاصيل المادة. تُنشأ وحدة مفردة باسم `feature-subject` لتجنب تداخل المسؤوليات.

### 1.4 شاشة التهيئة

المرحلة الثامنة تملك `ActivityPreparationDestination` وRepository آمنًا مع منع التكرار. أي متابعة نشاط من صفحة المادة يجب أن تبني وجهة التهيئة الحالية، لا أن تبدأ جلسة مباشرة.

## 2. مصادر الحقيقة في الخادم والبيانات

### 2.1 التحقق من ارتباط المادة بالطالب

الدالة الحالية:

`api_student_home_curriculum_subjects(PDO $pdo, int $studentId, int $limit)`

تختار المواد المطابقة لصف الطالب ومنهج مدينته وترتيب الإدارة، مع fallback تاريخي إلى `student_subject_state`. صفحة المادة يجب أن تعيد استخدام هذا الاختيار أو تبني تحققًا مطابقًا له، ثم تبحث عن `subject_version_id` المطلوب داخل النطاق المسموح فقط.

### 2.2 هوية المادة والمنهج

الجداول المؤكدة:

- `subjects`
- `subject_versions`
- `cities`
- `app_users`

حقول مرشحة بعد فحص schema وقت التشغيل:

- الاسم من `subjects.name`.
- اسم نسخة المنهج من `subject_versions.version_name` أو سياق المنهج الحالي.
- `version_type` عند الحاجة كوسم داخلي، لا يعرض بدل الاسم.
- `structure_mode` لتحديد مادة وحدات أو دروس.
- `has_two_parts` أو الأجزاء الفعلية من المحتوى.

### 2.3 تقدم المادة والقلوب

الجدول المؤكد:

`student_subject_state`

الحقول التاريخية المؤكدة:

- `subject_xp`
- `hearts`
- `hearts_refill_date`

القرار:

- نقاط المادة يمكن عرضها عند وجود العمود والسجل.
- القلوب تعاد بحد أقصى 3 وفق القاعدة الرسمية.
- `hearts_refill_date` تاريخي وقد لا يمثل قاعدة القلب كل ثماني ساعات بصورة دقيقة في كل تثبيت؛ لا يُحوّل إلى عد تنازلي إلا إذا ثبتت صيغة زمنية صالحة ودلالة مؤكدة. وإلا `next_restore.available=false`.
- لا يوجد مصدر مثبت لمستوى المادة أو حدود المستوى؛ يعاد المستوى والتقدم بحالة غير متاحة بدل اشتقاق معادلة من الواجهة.

### 2.4 نوع المحتوى والأجزاء

الجداول المؤكدة:

- `units(subject_version_id, part, ...)`
- `lessons(subject_version_id, unit_id, part, ...)`
- `content_nodes`
- `learning_path_items`

`subject_versions.structure_mode` يفرق بين الأنماط عندما يكون العمود موجودًا. الأجزاء يمكن استخلاصها باستعلام مجمع من `units/lessons`، دون تحميل بطاقات المحتوى وقواعد الفتح في هذه المرحلة.

قرار المرحلة العاشرة:

- إعادة `content.structure_mode`.
- إعادة قائمة أجزاء خفيفة: رقم الجزء وعدد الوحدات/الدروس فقط.
- إعادة `content.details_available=false` ورسالة واضحة بأن التفاصيل تملكها المرحلة الثانية عشرة.
- لا تحميل أسئلة أو حالات فتح أو محاولات.

### 2.5 آخر نشاط

الجدول:

`student_last_activity(student_id, subject_version_id, unit_id, mode, updated_at)`

يمكن إعادة نشاط المادة المطلوب باستعلام واحد. إذا توفر `unit_id` موجب ونمط نشاط صالح، تعرض الصفحة زر متابعة يفتح شاشة التهيئة. لا يبدأ النشاط مباشرة.

### 2.6 الوسائط والوصول

لا يوجد في API الحالية مفتاح وسائط مؤكد للمادة أو سياسة وصول تفصيلية موحدة. القرار:

- `media.available=false` مع fallback محلي.
- `access` يعاد بالحالة المؤكدة فقط. وجود المادة ضمن اختيار السياق يسمح بفتح الصفحة، لكنه لا يثبت الاشتراك أو المجانية لكل نشاط.

## 3. عقد API المقترح

المسار:

`GET /api/v1/student/subjects/{subject_version_id}`

الاستجابة تشمل:

- `student_id`, `subject_version_id`, `version`, `generated_at`.
- `identity`: الاسم، المنهج، نوع النسخة، الوسائط.
- `progress`: نقاط المادة، المستوى والتقدم بحالات availability مستقلة.
- `hearts`: current/max وnext_restore.
- `access`.
- `content`: structure_mode والأجزاء وملخص مؤجل للمرحلة 12.
- `last_activity`.
- `actions`: training_center contract availability وresume preparation contract.

ضوابط:

- Bearer session فقط.
- المعرف من path موجب ومتحقق داخل مواد الطالب.
- 404 ثابتة للمادة غير المرتبطة دون كشف وجودها خارج النطاق.
- لا `user_id` من الطلب.
- لا أسئلة ولا HTML دروس ولا N+1.
- لا إنشاء state أو خصم أو تعديل بيانات عند فتح الصفحة.

## 4. خطة Android

- إنشاء `feature-subject`.
- `StudentSubjectApi` وDTOs داخل `core-network`.
- Repository يدعم refresh token ويتحقق من تطابق الطالب والمادة.
- DataStore Cache متعدد المواد بمفتاح مركب للطالب والمادة، أو سجل واحد لكل صفحة مع تحقق صارم من المالك والمعرف.
- ViewModel cache-first ثم background refresh ومنع الطلب المكرر.
- واجهة RTL برأس قابل للانكماش، تقدم وقلوب وأجزاء خفيفة وإجراءات.
- ربط `StudentDestination.SubjectDetails` بالوحدة الجديدة.
- عقد مركز التدريب يبقى وجهة typed مؤجلة للمرحلة 11.
- متابعة النشاط تمر عبر `ActivityPreparationDestination` الحالية.

## 5. ما لن يُنفذ في المرحلة العاشرة

- أدوات مركز التدريب الست.
- بطاقات الوحدات والدروس وحالات القفل والمحاولات والمراجعة.
- تحميل الأسئلة.
- شراء القلب أو الجواهر.
- معادلة مستوى أو تقدم غير مثبتة.
- ترحيل قاعدة البيانات أو تعديل الإنتاج.

## 6. بوابة القبول

- منع IDOR للمادة.
- تطابق OpenAPI وFixture وPHP وAndroid.
- Cache معزول حسب الطالب والمادة.
- حالات Offline والجلسة المنتهية والحقول الجزئية.
- نجاح Android unit tests وLint وبناء APKs الثلاثة.
- نجاح PHP syntax والاختبارات السلوكية وفحص المواد الخاصة.
- عدم تعديل Push V2 أو PWA أو service worker أو قاعدة الإنتاج.
