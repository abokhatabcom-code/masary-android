package app.masary.feature.home.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StudentHomeScreenTest {
    @Test
    fun `uses first and second names in greeting`() {
        assertEquals("مهيب صدام", shortDisplayName("مهيب صدام أحمد"))
    }

    @Test
    fun `falls back when display name is empty`() {
        assertEquals("طالب مساري", shortDisplayName("   "))
    }

    @Test
    fun `bottom navigation exposes approved five typed destinations in order`() {
        assertEquals(
            listOf(
                StudentDestination.Home,
                StudentDestination.Guide,
                StudentDestination.Subjects,
                StudentDestination.Ranking,
                StudentDestination.Profile,
            ),
            studentDestinations,
        )
    }

    @Test
    fun `unknown external destination falls back to home`() {
        assertEquals(StudentDestination.Home, externalStudentDestination("../../admin"))
        assertEquals(StudentDestination.Guide, externalStudentDestination("guide"))
    }
}
