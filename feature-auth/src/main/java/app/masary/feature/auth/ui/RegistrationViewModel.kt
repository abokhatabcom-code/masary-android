package app.masary.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.masary.core.datastore.SessionManager
import app.masary.core.models.auth.StudentSession
import app.masary.feature.auth.domain.*
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegistrationState(
    val step: Int = 1,
    val draft: RegistrationDraft = RegistrationDraft(),
    val cities: LookupState<RegistrationCity> = LookupState.Loading,
    val grades: LookupState<AcademicOption> = LookupState.Loading,
    val schools: LookupState<AcademicOption> = LookupState.Empty,
    val submitting: Boolean = false,
    val error: String? = null,
    val showCancelConfirmation: Boolean = false,
)

class RegistrationViewModel(
    private val savedState: SavedStateHandle,
    private val repository: RegistrationRepository,
    private val sessionManager: SessionManager,
    private val deviceName: String,
    private val onSuccess: (StudentSession) -> Unit,
    private val onCancel: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(restoreState())
    val state = _state.asStateFlow()
    private var idempotencyKey: String? = savedState[KEY_IDEMPOTENCY]

    init {
        if (_state.value.cities is LookupState.Loading) loadCities()
        if (_state.value.grades is LookupState.Loading) loadGrades()
        _state.value.draft.city?.takeIf { it.requiresSchool }?.let(::loadSchools)
    }

    fun beginRegistration() { if (idempotencyKey == null) idempotencyKey = newKey() }

    fun updateDraft(draft: RegistrationDraft) {
        _state.value = _state.value.copy(draft = draft, error = null)
        persist()
    }

    fun nextAccount() {
        if (!_state.value.draft.accountValid()) {
            _state.value = _state.value.copy(error = "تحقق من بيانات الحساب والبريد الإلكتروني.")
            return
        }
        setStep(2)
    }

    fun selectCity(city: RegistrationCity) {
        updateDraft(_state.value.draft.copy(city = city, school = null))
        _state.value = _state.value.copy(schools = if (city.requiresSchool) LookupState.Loading else LookupState.Empty)
        if (city.requiresSchool) loadSchools(city)
    }

    fun nextAcademic() {
        if (!_state.value.draft.academicValid()) {
            _state.value = _state.value.copy(error = "اختر البيانات الأكاديمية المطلوبة.")
            return
        }
        setStep(3)
    }

    fun back() = setStep((_state.value.step - 1).coerceAtLeast(1))
    fun retryCities() = loadCities()
    fun retryGrades() = loadGrades()
    fun retrySchools() = _state.value.draft.city?.let(::loadSchools)

    fun requestCancel() {
        if (_state.value.draft.hasUserInput()) _state.value = _state.value.copy(showCancelConfirmation = true)
        else confirmCancel()
    }

    fun dismissCancel() { _state.value = _state.value.copy(showCancelConfirmation = false) }

    fun confirmCancel() {
        reset()
        onCancel()
    }

    fun submit(privacyAccepted: Boolean) {
        val draft = _state.value.draft.copy(privacyAccepted = privacyAccepted)
        updateDraft(draft)
        if (!privacyAccepted) {
            _state.value = _state.value.copy(error = "يجب الموافقة على سياسة الخصوصية.")
            return
        }
        if (_state.value.submitting) return
        _state.value = _state.value.copy(submitting = true, error = null)
        viewModelScope.launch {
            repository.register(draft, deviceName, idempotencyKey ?: newKey().also { idempotencyKey = it })
                .onSuccess { authenticated ->
                    try {
                        sessionManager.save(authenticated)
                        val student = authenticated.student
                        reset()
                        onSuccess(StudentSession(student.id, student.username, student.displayName))
                    } catch (_: Throwable) {
                        _state.value = _state.value.copy(submitting = false, error = "تعذر حفظ الجلسة بأمان. حاول مرة أخرى.")
                    }
                }
                .onFailure { failure ->
                    val kind = (failure as? RegistrationException)?.kind
                    _state.value = _state.value.copy(
                        submitting = false,
                        error = when (kind) {
                            RegistrationFailureKind.CONFLICT -> "اسم المستخدم مستخدم أو تغيرت بيانات الطلب. ابدأ طلبًا جديدًا."
                            RegistrationFailureKind.VALIDATION -> "تحقق من بيانات التسجيل ثم حاول مرة أخرى."
                            RegistrationFailureKind.RETRYABLE -> "تعذر الاتصال بالخدمة. احتفظنا ببياناتك ويمكنك إعادة المحاولة."
                            else -> "تعذر إكمال التسجيل حاليًا. حاول مرة أخرى."
                        },
                    )
                    // Keep the same key and all sensitive input for every failed attempt.
                }
        }
    }

    private fun loadCities() = loadLookup(
        loading = { _state.value = _state.value.copy(cities = LookupState.Loading) },
        request = repository::cities,
        update = { _state.value = _state.value.copy(cities = it) },
    )

    private fun loadGrades() = loadLookup(
        loading = { _state.value = _state.value.copy(grades = LookupState.Loading) },
        request = repository::grades,
        update = { _state.value = _state.value.copy(grades = it) },
    )

    private fun loadSchools(city: RegistrationCity) = loadLookup(
        loading = { _state.value = _state.value.copy(schools = LookupState.Loading) },
        request = { repository.schools(city.id) },
        update = { _state.value = _state.value.copy(schools = it) },
    )

    private fun <T> loadLookup(loading: () -> Unit, request: suspend () -> Result<List<T>>, update: (LookupState<T>) -> Unit) {
        loading()
        viewModelScope.launch {
            request().fold(
                onSuccess = { update(if (it.isEmpty()) LookupState.Empty else LookupState.Ready(it)) },
                onFailure = { update(LookupState.Error) },
            )
        }
    }

    private fun setStep(step: Int) { _state.value = _state.value.copy(step = step, error = null); persist() }

    private fun reset() {
        savedState.keys().filter { it.startsWith("registration.") }.forEach { savedState.remove<Any>(it) }
        idempotencyKey = null
        _state.value = RegistrationState()
    }

    private fun newKey(): String = UUID.randomUUID().toString().also { savedState[KEY_IDEMPOTENCY] = it }

    private fun persist() {
        val d = _state.value.draft
        savedState[KEY_STEP] = _state.value.step
        savedState[KEY_DRAFT] = arrayListOf(d.fullName, d.username, d.phone, d.email, d.gender, d.personality, d.city?.id?.toString().orEmpty(), d.city?.name.orEmpty(), d.city?.requiresSchool?.toString().orEmpty(), d.school?.id?.toString().orEmpty(), d.school?.name.orEmpty(), d.grade?.id?.toString().orEmpty(), d.grade?.name.orEmpty(), d.privacyAccepted.toString())
    }

    private fun restoreState(): RegistrationState {
        val values = savedState.get<ArrayList<String>>(KEY_DRAFT) ?: return RegistrationState()
        fun value(index: Int) = values.getOrElse(index) { "" }
        val city = value(6).toLongOrNull()?.let { RegistrationCity(it, value(7), value(8).toBoolean()) }
        val school = value(9).toLongOrNull()?.let { AcademicOption(it, value(10)) }
        val grade = value(11).toLongOrNull()?.let { AcademicOption(it, value(12)) }
        return RegistrationState(step = savedState[KEY_STEP] ?: 1, draft = RegistrationDraft(fullName = value(0), username = value(1), phone = value(2), email = value(3), gender = value(4), personality = value(5), city = city, school = school, grade = grade, privacyAccepted = value(13).toBoolean()))
    }

    companion object {
        private const val KEY_STEP = "registration.step"
        private const val KEY_DRAFT = "registration.draft"
        private const val KEY_IDEMPOTENCY = "registration.idempotency"
    }
}
