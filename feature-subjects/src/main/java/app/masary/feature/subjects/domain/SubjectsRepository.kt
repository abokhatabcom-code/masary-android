package app.masary.feature.subjects.domain

interface SubjectsRepository {
    suspend fun loadSnapshot(): StudentSubjectsData?
    suspend fun loadSubjects(): Result<StudentSubjectsData>
    suspend fun clearSnapshot()
}

interface SubjectsSnapshotStore {
    suspend fun read(studentId: String): StudentSubjectsData?
    suspend fun write(studentId: String, data: StudentSubjectsData)
    suspend fun clear()
}

class SubjectsSessionExpiredException(
    message: String = "انتهت جلسة الدخول. سجّل الدخول من جديد.",
    cause: Throwable? = null,
) : Exception(message, cause)

class SubjectsNetworkException(
    message: String = "تعذر الاتصال بالمنصة. تحقق من الإنترنت وحاول مرة أخرى.",
    cause: Throwable? = null,
) : Exception(message, cause)

class SubjectsServiceException(
    message: String = "تعذر تحميل المواد الآن.",
    cause: Throwable? = null,
) : Exception(message, cause)
