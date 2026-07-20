package app.masary.feature.home.domain

interface HomeRepository {
    suspend fun loadHome(): Result<StudentHomeData>
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
