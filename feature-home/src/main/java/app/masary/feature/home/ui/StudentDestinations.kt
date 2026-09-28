package app.masary.feature.home.ui

import app.masary.feature.home.R
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface StudentDestination {
    @Serializable data object Home : StudentDestination
    @Serializable data object Guide : StudentDestination
    @Serializable data object Subjects : StudentDestination
    @Serializable data object Ranking : StudentDestination
    @Serializable data object Profile : StudentDestination

    @Serializable
    data class SubjectDetails(
        val subjectVersionId: Int,
        val subjectName: String = "",
        val curriculumLabel: String = "",
        val unitId: Int? = null,
        val actionKey: String? = null,
    ) : StudentDestination
}

internal val studentDestinations: List<StudentDestination> = listOf(
    StudentDestination.Home,
    StudentDestination.Guide,
    StudentDestination.Subjects,
    StudentDestination.Ranking,
    StudentDestination.Profile,
)

internal val StudentDestination.labelRes: Int
    get() = when (this) {
        StudentDestination.Home -> R.string.nav_home
        StudentDestination.Guide -> R.string.nav_guide
        StudentDestination.Subjects -> R.string.nav_subjects
        StudentDestination.Ranking -> R.string.nav_ranking
        StudentDestination.Profile -> R.string.nav_profile
        is StudentDestination.SubjectDetails -> R.string.nav_subjects
    }

internal fun externalStudentDestination(value: String): StudentDestination = when (value.trim().lowercase()) {
    "home", "studentdestination.home" -> StudentDestination.Home
    "guide", "studentdestination.guide" -> StudentDestination.Guide
    "subjects", "studentdestination.subjects" -> StudentDestination.Subjects
    "ranking", "studentdestination.ranking" -> StudentDestination.Ranking
    "profile", "studentdestination.profile" -> StudentDestination.Profile
    else -> StudentDestination.Home
}
