package app.masary.feature.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.masary.feature.auth.domain.*

@Composable
fun RegistrationScreen(
    state: RegistrationState,
    onDraftChange: (RegistrationDraft) -> Unit,
    onNextAccount: () -> Unit,
    onCity: (RegistrationCity) -> Unit,
    onNextAcademic: () -> Unit,
    onSubmit: (Boolean) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onDismissCancel: () -> Unit,
    onConfirmCancel: () -> Unit,
    retryCities: () -> Unit,
    retryGrades: () -> Unit,
    retrySchools: () -> Unit,
) {
    Surface {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إنشاء حساب طالب — الخطوة ${state.step} من 3", style = MaterialTheme.typography.headlineSmall)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when (state.step) {
                1 -> AccountStep(state.draft, onDraftChange, onNextAccount)
                2 -> AcademicStep(state, onDraftChange, onCity, onNextAcademic, retryCities, retryGrades, retrySchools)
                else -> ReviewStep(state, onDraftChange, onSubmit)
            }
            Row {
                if (state.step > 1) TextButton(onBack) { Text("السابق") }
                TextButton(onCancel) { Text("إلغاء التسجيل") }
            }
        }
    }
    if (state.showCancelConfirmation) AlertDialog(
        onDismissRequest = onDismissCancel,
        title = { Text("إلغاء التسجيل؟") },
        text = { Text("ستُمسح جميع البيانات التي أدخلتها، بما فيها كلمة المرور.") },
        confirmButton = { TextButton(onConfirmCancel) { Text("مسح وإلغاء") } },
        dismissButton = { TextButton(onDismissCancel) { Text("متابعة التسجيل") } },
    )
}

@Composable private fun AccountStep(d: RegistrationDraft, update: (RegistrationDraft) -> Unit, next: () -> Unit) {
    Field("الاسم الكامل", d.fullName) { update(d.copy(fullName = it)) }
    Field("اسم المستخدم", d.username) { update(d.copy(username = it)) }
    Field("الهاتف (اختياري)", d.phone) { update(d.copy(phone = it)) }
    Field("البريد الإلكتروني (اختياري)", d.email) { update(d.copy(email = it)) }
    Field("كلمة المرور", d.password, true) { update(d.copy(password = it)) }
    Field("تأكيد كلمة المرور", d.passwordConfirmation, true) { update(d.copy(passwordConfirmation = it)) }
    Choice("الجنس", listOf("male" to "ذكر", "female" to "أنثى"), d.gender) { update(d.copy(gender = it)) }
    Choice("النمط (اختياري)", listOf("explorer" to "مستكشف", "achiever" to "منجز", "calm" to "هادئ"), d.personality) { update(d.copy(personality = it)) }
    Button(next, Modifier.fillMaxWidth()) { Text("التالي") }
}

@Composable private fun AcademicStep(state: RegistrationState, update: (RegistrationDraft) -> Unit, cityChanged: (RegistrationCity) -> Unit, next: () -> Unit, retryCities: () -> Unit, retryGrades: () -> Unit, retrySchools: () -> Unit) {
    Lookup("المدن", state.cities, retryCities) { cities -> Choice("المدينة", cities.map { it.id.toString() to it.name }, state.draft.city?.id?.toString().orEmpty()) { id -> cityChanged(cities.first { it.id.toString() == id }) } }
    if (state.draft.city?.requiresSchool == true) Lookup("المدارس", state.schools, retrySchools) { schools -> Choice("المدرسة", schools.map { it.id.toString() to it.name }, state.draft.school?.id?.toString().orEmpty()) { id -> update(state.draft.copy(school = schools.first { it.id.toString() == id })) } }
    Lookup("الصفوف", state.grades, retryGrades) { grades -> Choice("الصف", grades.map { it.id.toString() to it.name }, state.draft.grade?.id?.toString().orEmpty()) { id -> update(state.draft.copy(grade = grades.first { it.id.toString() == id })) } }
    Button(next, Modifier.fillMaxWidth()) { Text("المراجعة") }
}

@Composable private fun ReviewStep(state: RegistrationState, update: (RegistrationDraft) -> Unit, submit: (Boolean) -> Unit) {
    val d = state.draft
    Text("الاسم: ${d.fullName}\nالمستخدم: ${d.username}\nالمدينة: ${d.city?.name}\nالمدرسة: ${d.school?.name ?: "غير مطلوبة"}\nالصف: ${d.grade?.name}")
    Row { Checkbox(d.privacyAccepted, { update(d.copy(privacyAccepted = it)) }); Text("أوافق على سياسة الخصوصية") }
    Button({ submit(d.privacyAccepted) }, Modifier.fillMaxWidth(), enabled = !state.submitting) { Text(if (state.submitting) "جارٍ الإنشاء…" else "إنشاء الحساب") }
}

@Composable private fun <T> Lookup(label: String, state: LookupState<T>, retry: () -> Unit, content: @Composable (List<T>) -> Unit) = when (state) {
    LookupState.Loading -> Row { CircularProgressIndicator(Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text("جارٍ تحميل $label…") }
    LookupState.Empty -> Text("لا توجد $label متاحة حاليًا.")
    LookupState.Error -> Column { Text("تعذر تحميل $label."); TextButton(retry) { Text("إعادة المحاولة") } }
    is LookupState.Ready -> content(state.items)
}

@Composable private fun Field(label: String, value: String, password: Boolean = false, change: (String) -> Unit) = OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None)
@Composable private fun Choice(label: String, items: List<Pair<String, String>>, selected: String, select: (String) -> Unit) { Text(label); items.forEach { (id, name) -> Row { RadioButton(selected == id, { select(id) }); Text(name) } } }
