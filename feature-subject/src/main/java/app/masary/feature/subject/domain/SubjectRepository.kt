package app.masary.feature.subject.domain

import app.masary.core.models.student.StudentLiveState
import kotlinx.coroutines.flow.Flow

interface SubjectRepository {
    fun observeLiveState(): Flow<StudentLiveState>
    suspend fun loadSubject(subjectVersionId: Int): Result<StudentSubjectPage>
    suspend fun loadSnapshot(subjectVersionId: Int): StudentSubjectPage?
    suspend fun clearSnapshots()
}

interface SubjectSnapshotStore {
    suspend fun read(studentId: String, subjectVersionId: Int): StudentSubjectPage?
    suspend fun write(studentId: String, subjectVersionId: Int, data: StudentSubjectPage)
    suspend fun clear()
}

open class SubjectPageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class SubjectPageNetworkException(cause: Throwable? = null) :
    SubjectPageException("تعذر الاتصال بالخادم. سنعرض آخر نسخة مؤكدة إن كانت متاحة.", cause)
class SubjectPageServiceException(message: String = "تعذر تحميل المادة الآن.", cause: Throwable? = null) :
    SubjectPageException(message, cause)
class SubjectPageSessionExpiredException(message: String = "انتهت جلسة الدخول.", cause: Throwable? = null) :
    SubjectPageException(message, cause)
class SubjectPageNotFoundException(message: String = "المادة غير متاحة لهذا الحساب.", cause: Throwable? = null) :
    SubjectPageException(message, cause)
