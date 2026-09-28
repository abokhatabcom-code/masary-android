# المرحلة 14 — Masary Design System Foundation

مرجع تقني للهوية البصرية ونظام الواجهات في تطبيقات مساري.

> هذه الوثيقة لا تعتمد شكلًا نهائيًا وحدها. أي قرار بصري واسع الانتشار يمر عبر بوابة الاعتماد البصري في Issue #61.

## 1. الوضع الحالي

يوجد أساس مركزي في:

`core-ui/src/main/java/app/masary/core/ui/MasaryTheme.kt`

ويحتوي حاليًا على:

- Brand palette أولية:
  - Logo Navy
  - Navy / Navy Deep / Navy Soft
  - Gold / Gold Bright
- Light / Dark Material 3 color schemes.
- Typography مبنية على SansSerif.
- Shape scale: 8 / 12 / 18 / 24 / 30 dp.
- Spacing: 4 / 8 / 16 / 24 / 32 / 48 dp.
- Elevation: 0 / 2 / 5 / 10 dp.
- Status colors: success / warning / error / info.
- Compatibility aliases قديمة باسم purple.

هذا أساس جيد، لكنه Theme وليس Design System كاملًا.

## 2. المشاكل التي تعالجها المرحلة 14

1. اللون يُستخدم أحيانًا كقيمة Brand مباشرة بدل role دلالي.
2. لا توجد طبقة Components موحدة للشاشات.
3. حالات loading/error/empty/disabled ليست موحدة بصريًا.
4. لا يوجد contract موثق للـRTL والمسافات والأيقونات.
5. لا يوجد مرجع واحد لأحجام العناصر التفاعلية.
6. بعض Features تعتمد على Material components مباشرة مع تخصيص محلي.
7. compatibility aliases القديمة تعني أن الهوية لم تكتمل هجرتها.
8. لا يوجد Visual acceptance baseline ثابت.

## 3. البنية المستهدفة داخل core-ui

اقتراح تنظيم مبدئي:

```
core-ui/
  theme/
    MasaryTheme.kt
    MasaryColorTokens.kt
    MasaryTypography.kt
    MasaryShapes.kt
    MasarySpacing.kt
    MasaryMotion.kt
  components/
    MasaryButton.kt
    MasaryCard.kt
    MasaryTopBar.kt
    MasaryBottomNavigation.kt
    MasaryTextField.kt
    MasaryChip.kt
    MasaryProgress.kt
    MasaryStateViews.kt
    MasaryDialog.kt
  preview/
    MasaryDesignSystemPreview.kt
```

التقسيم النهائي يُعتمد أثناء 14.1 قبل نقل الملفات بكثافة.

## 4. Brand vs Semantic Tokens

يجب الفصل بين:

### Brand tokens
ألوان الهوية نفسها مثل:
- navy
- gold

### Semantic tokens
ما تستخدمه الواجهة:
- background
- surface
- surfaceElevated
- textPrimary
- textSecondary
- textMuted
- border
- accent
- success
- warning
- error
- info
- disabledContainer
- disabledContent

الشاشات يجب أن تعتمد Semantic roles قدر الإمكان، لا درجات Brand الخام.

## 5. Typography

المطلوب في 14.1:

- اعتماد family عربية/لاتينية مناسبة للمنتج أو قرار واضح بالاعتماد على system font.
- تعريف أدوار نصية واضحة:
  - Display
  - Screen Title
  - Section Title
  - Card Title
  - Body
  - Supporting
  - Label
  - Numeric/Score
- اختبار:
  - Arabic diacritics.
  - أرقام عربية/لاتينية.
  - Font scaling حتى 200%.
  - RTL line wrapping.

لا يتم تضمين ملفات خطوط داخل المستودع إلا وفق ترخيص واضح، ولا تُعامل ملفات الخطوط كجزء من handoff خارجي.

## 6. Shape / Spacing / Touch

- كل spacing يأتي من scale مركزي.
- minimum interactive target: 48dp.
- card radius/button radius لا يُختار محليًا في كل Feature.
- الحواف والمسافات يجب أن تحافظ على اتزان RTL.

## 7. Component Contract

كل component رسمي يحدد بوضوح:

- API مختصر.
- semantic colors.
- typography role.
- enabled/disabled.
- loading عند الحاجة.
- error عند الحاجة.
- RTL.
- accessibility semantics.
- Compose Preview.

## 8. App Shell

قبل إعادة تصميم كل شاشة، نعتمد أولًا:

1. Screen background.
2. Top bar.
3. Bottom navigation.
4. Primary / Secondary button.
5. Card.
6. Text field.
7. State views.

هذه العناصر تصبح النموذج المرجعي لبقية التطبيق.

## 9. عدم كسر الأساس الوظيفي

المرحلة 14 لا تغيّر:

- عقود API.
- session ownership.
- Local-First semantics.
- pending operations.
- Offline answer queue.
- result confirmation.
- إعدادات /admin/tests/ الوظيفية.

الواجهة تستهلك الحالة الموجودة ولا تعيد اختراع منطقها.

## 10. خطة 14.1

- [ ] استخراج الألوان من MasaryTheme إلى tokens منظمة.
- [ ] تعريف semantic palette للـLight/Dark.
- [ ] توثيق palette مع previews.
- [ ] تثبيت Typography roles.
- [ ] تثبيت spacing/shape/elevation/motion scales.
- [ ] إضافة CompositionLocal/API مناسب للتوكنز غير الموجودة في MaterialTheme.
- [ ] إزالة أي naming قديم لا يمثل الهوية بعد migration آمنة.
- [ ] إضافة design-system gallery/preview.
- [ ] اختيار شاشة مرجعية أولى لتطبيق النظام عليها.

## 11. بوابة اعتماد قبل التعميم

لا نبدأ إعادة تصميم كل الشاشات دفعة واحدة.

نطبق النظام أولًا على نموذج مرجعي محدود، ثم نراجع:
- اللون.
- الخط.
- كثافة المعلومات.
- شكل البطاقة.
- الأزرار.
- شريط التنقل.
- RTL.
- Dark mode.

بعد الاعتماد يبدأ التعميم المنظم.
