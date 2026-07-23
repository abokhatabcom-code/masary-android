package app.masary.feature.home.domain

interface HomeRepository {
    suspend fun loadSnapshot(): StudentHomeData?
    suspend fun loadHome(): Result<StudentHomeData>
    suspend fun clearSnapshot()
}

interface HomeSnapshotStore {
    suspend fun read(studentId: String): StudentHomeData?
    suspend fun write(studentId: String, data: StudentHomeData)
    suspend fun clear()
}

class HomeSessionExpiredException(
    message: String = "انتهت جلسة الدخول. سجّل الدخول من جديد.",
    cause: Throwable? = null,
) : Exception(message, cause)

class HomeNetworkException(
    message: String = "تعذر الاتصال بالمنصة. تحقق من الإنترنت وحاول مرة أخرى.",
    cause: Throwable? = null,
) : Exception(message, cause)

class HomeServiceException(
    message: String = "تعذر تحميل الصفحة الرئيسية الآن.",
    cause: Throwable? = null,
) : Exception(message, cause)
