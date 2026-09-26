package app.masary.feature.trainingcenter.domain

interface TrainingCenterRepository {
    suspend fun loadTrainingCenter(subjectVersionId: Int): Result<StudentTrainingCenter>
    suspend fun loadSnapshot(subjectVersionId: Int): StudentTrainingCenter?
    suspend fun clearSnapshots()
}

interface TrainingCenterSnapshotStore {
    suspend fun read(studentId: String, subjectVersionId: Int): StudentTrainingCenter?
    suspend fun write(studentId: String, subjectVersionId: Int, data: StudentTrainingCenter)
    suspend fun clear()
}

open class TrainingCenterException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class TrainingCenterNetworkException(cause: Throwable? = null) :
    TrainingCenterException("تعذر الاتصال بالخادم. سنعرض آخر نسخة مؤكدة إن كانت متاحة.", cause)

class TrainingCenterServiceException(
    message: String = "تعذر تحميل مركز التدريب الآن.",
    cause: Throwable? = null,
) : TrainingCenterException(message, cause)

class TrainingCenterSessionExpiredException(
    message: String = "انتهت جلسة الدخول.",
    cause: Throwable? = null,
) : TrainingCenterException(message, cause)

class TrainingCenterNotFoundException(
    message: String = "مركز التدريب غير متاح لهذه المادة.",
    cause: Throwable? = null,
) : TrainingCenterException(message, cause)
