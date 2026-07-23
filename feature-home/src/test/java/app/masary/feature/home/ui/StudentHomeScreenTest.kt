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
    fun `bottom navigation exposes five typed destinations`() {
        assertEquals(5, studentDestinations.size)
        assertEquals(5, studentDestinations.map { it::class }.distinct().size)
    }
}
