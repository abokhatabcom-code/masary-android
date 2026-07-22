package app.masary.core.network

import app.masary.core.network.auth.StudentLoginResponseDto
import app.masary.core.network.auth.StudentLogoutResponseDto
import app.masary.core.network.auth.StudentMeResponseDto
import app.masary.core.network.auth.StudentRefreshResponseDto
import app.masary.core.network.home.StudentHomeResponseDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiContractFixtureTest {
    private val gson = Gson()

    @Test
    fun `success fixtures match Android DTOs`() {
        val login = parse("login-success.json", StudentLoginResponseDto::class.java)
        assertTrue(login.success)
        assertEquals("42", login.data?.student?.id)
        assertEquals(900L, login.data?.expiresIn)

        val refresh = parse("refresh-success.json", StudentRefreshResponseDto::class.java)
        assertTrue(refresh.success)
        assertNotNull(refresh.data?.refreshToken)

        val logout = parse("logout-success.json", StudentLogoutResponseDto::class.java)
        assertTrue(logout.data?.loggedOut == true)

        val me = parse("me-success.json", StudentMeResponseDto::class.java)
        assertEquals("contract.student", me.data?.student?.username)

        val home = parse("home-success.json", StudentHomeResponseDto::class.java)
        assertTrue(home.success)
        assertEquals(120, home.data?.summary?.globalXp)
    }

    @Test
    fun `error envelope matches every Android response shape`() {
        val json = resource("error.json")
        listOf(
            gson.fromJson(json, StudentLoginResponseDto::class.java),
            gson.fromJson(json, StudentRefreshResponseDto::class.java),
            gson.fromJson(json, StudentLogoutResponseDto::class.java),
            gson.fromJson(json, StudentMeResponseDto::class.java),
            gson.fromJson(json, StudentHomeResponseDto::class.java),
        ).forEach { response ->
            val success = response.javaClass.getMethod("getSuccess").invoke(response) as Boolean
            assertFalse(success)
        }
    }

    private fun <T> parse(name: String, type: Class<T>): T = gson.fromJson(resource(name), type)

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("contracts/$name"))
            .readText()
}
