package app.masary.student.auth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginStudentTest {
    @Test
    fun `blank student id is rejected without calling repository`() {
        var called = false
        val password = "temporary".toCharArray()
        val result = LoginStudent { _, _ -> called = true; true }("  ", password)

        assertEquals(LoginResult.InvalidStudentId, result)
        assertEquals(false, called)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun `empty password is rejected`() {
        val result = LoginStudent { _, _ -> true }("student", charArrayOf())

        assertEquals(LoginResult.InvalidPassword, result)
    }

    @Test
    fun `valid input delegates and clears password`() {
        val password = "temporary".toCharArray()
        val result = LoginStudent { id, provided ->
            id == "student" && provided.contentEquals("temporary".toCharArray())
        }(" student ", password)

        assertEquals(LoginResult.Success, result)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun `repository rejection is returned`() {
        val result = LoginStudent { _, _ -> false }("student", "wrong".toCharArray())

        assertEquals(LoginResult.Rejected, result)
    }
}
