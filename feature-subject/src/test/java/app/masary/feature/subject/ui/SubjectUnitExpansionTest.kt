package app.masary.feature.subject.ui

import app.masary.feature.subject.domain.SubjectActionAvailability
import app.masary.feature.subject.domain.SubjectLearningState
import app.masary.feature.subject.domain.SubjectLearningStatus
import app.masary.feature.subject.domain.SubjectLesson
import app.masary.feature.subject.domain.SubjectUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubjectUnitExpansionTest {
    @Test
    fun lastActivityUnitWinsWhenPresent() {
        val units = listOf(
            unit(1, SubjectLearningStatus.Unknown),
            unit(2, SubjectLearningStatus.InProgress),
        )

        assertEquals(1, preferredExpandedUnitId(units, 1))
    }

    @Test
    fun inProgressUnitIsPreferredWithoutLastActivity() {
        val units = listOf(
            unit(1, SubjectLearningStatus.Unknown),
            unit(2, SubjectLearningStatus.InProgress),
        )

        assertEquals(2, preferredExpandedUnitId(units, null))
    }

    @Test
    fun inProgressLessonMakesItsUnitPreferred() {
        val units = listOf(
            unit(
                id = 1,
                status = SubjectLearningStatus.Unknown,
                lessons = listOf(lesson(11, SubjectLearningStatus.Unknown)),
            ),
            unit(
                id = 2,
                status = SubjectLearningStatus.Unknown,
                lessons = listOf(lesson(21, SubjectLearningStatus.InProgress)),
            ),
        )

        assertEquals(2, preferredExpandedUnitId(units, null))
    }

    @Test
    fun firstUnitIsFallbackAndEmptyListReturnsNull() {
        assertEquals(
            7,
            preferredExpandedUnitId(
                listOf(unit(7, SubjectLearningStatus.Unknown)),
                lastActivityUnitId = 999,
            ),
        )
        assertNull(preferredExpandedUnitId(emptyList(), null))
    }

    private fun unit(
        id: Int,
        status: SubjectLearningStatus,
        lessons: List<SubjectLesson> = emptyList(),
    ) = SubjectUnit(
        id = id,
        partNumber = 0,
        title = "وحدة $id",
        position = id,
        state = SubjectLearningState(status, ""),
        lessons = lessons,
    )

    private fun lesson(
        id: Int,
        status: SubjectLearningStatus,
    ) = SubjectLesson(
        id = id,
        unitId = 1,
        partNumber = 0,
        title = "درس $id",
        position = id,
        state = SubjectLearningState(status, ""),
        preparation = SubjectActionAvailability(false, ""),
    )
}
