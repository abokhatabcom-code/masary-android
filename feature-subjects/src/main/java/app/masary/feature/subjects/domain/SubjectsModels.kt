package app.masary.feature.subjects.domain

data class StudentSubjectsData(
    val studentId: String,
    val version: String,
    val generatedAt: String,
    val complete: Boolean,
    val academic: SubjectsAcademicContext,
    val subjects: List<StudentSubject>,
    val empty: SubjectsEmptyState,
    val snapshot: SubjectsSnapshotMetadata? = null,
)

data class SubjectsAcademicContext(
    val available: Boolean,
    val gradeName: String,
    val departmentName: String,
    val cityName: String,
    val curriculumName: String,
    val reason: String,
) {
    val displayLabel: String
        get() = listOf(gradeName, departmentName, curriculumName, cityName)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" • ")
}

data class StudentSubject(
    val subjectVersionId: Int,
    val name: String,
    val hearts: Int,
    val curriculumLabel: String,
    val progress: SubjectProgress,
    val media: SubjectMedia,
    val access: SubjectAccess,
    val lastActivity: SubjectLastActivity,
)

data class SubjectProgress(
    val available: Boolean,
    val percent: Int?,
    val reason: String,
)

data class SubjectMedia(
    val available: Boolean,
    val key: String?,
    val reason: String,
)

data class SubjectAccess(
    val available: Boolean,
    val status: SubjectAccessStatus,
    val reason: String,
)

enum class SubjectAccessStatus {
    Unknown,
    Available,
    Free,
    RequiresSubscription,
    Blocked,
}

data class SubjectLastActivity(
    val available: Boolean,
    val unitId: Int?,
    val mode: String,
    val updatedAt: String,
    val reason: String,
)

data class SubjectsEmptyState(
    val isEmpty: Boolean,
    val reason: String,
)

data class SubjectsSnapshotMetadata(val savedAtEpochMillis: Long)
