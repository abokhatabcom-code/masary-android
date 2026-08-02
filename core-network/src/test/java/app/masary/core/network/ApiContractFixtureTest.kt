package app.masary.core.network

import app.masary.core.network.activity.ActivityPreparationPreviewResponseDto
import app.masary.core.network.activity.ActivityStartResponseDto
import app.masary.core.network.auth.CitiesResponseDto
import app.masary.core.network.auth.GradesResponseDto
import app.masary.core.network.auth.SchoolsResponseDto
import app.masary.core.network.auth.StudentLoginResponseDto
import app.masary.core.network.auth.StudentLogoutResponseDto
import app.masary.core.network.auth.StudentMeResponseDto
import app.masary.core.network.auth.StudentRefreshResponseDto
import app.masary.core.network.home.StudentHomeResponseDto
import app.masary.core.network.subjects.StudentSubjectsResponseDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiContractFixtureTest {
    private val gson = Gson()

    @Test
    fun `success fixtures match Android DTOs`() {
        val cities = parse("cities-success.json", CitiesResponseDto::class.java)
        assertTrue(cities.data?.cities?.first()?.requiresSchool == true)
        assertEquals(7L, parse("grades-success.json", GradesResponseDto::class.java).data?.grades?.first()?.id)
        assertEquals(12L, parse("schools-success.json", SchoolsResponseDto::class.java).data?.schools?.first()?.id)
        val registration = parse("register-success.json", StudentLoginResponseDto::class.java)
        assertNotNull(registration.data?.refreshToken)

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
        assertEquals(14, home.data?.indicators?.globalRank)
        assertEquals("الرياضيات", home.data?.subjects?.single()?.name)
        assertEquals(null, home.data?.subjects?.single()?.progressPercent)
        assertEquals("news", home.data?.spotlight?.type)

        val subjects = parse("subjects-success.json", StudentSubjectsResponseDto::class.java)
        assertTrue(subjects.success)
        assertEquals("42", subjects.data?.studentId)
        assertEquals("منهج عدن", subjects.data?.academic?.curriculumName)
        assertEquals("الرياضيات", subjects.data?.subjects?.single()?.name)
        assertFalse(subjects.data?.subjects?.single()?.progress?.available == true)
        assertEquals(null, subjects.data?.subjects?.single()?.media?.key)
        assertTrue(subjects.data?.subjects?.single()?.lastActivity?.available == true)

        val preview = parse(
            "activity-preview-success.json",
            ActivityPreparationPreviewResponseDto::class.java,
        )
        assertTrue(preview.data?.eligibility?.available == true)
        assertEquals(3, preview.data?.balances?.hearts)
        assertEquals("الرياضيات", preview.data?.activity?.subjectName)

        val start = parse("activity-start-success.json", ActivityStartResponseDto::class.java)
        assertEquals("activity-session-001", start.data?.sessionId)
        assertEquals(0, start.data?.debit?.heartDebited)
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
            gson.fromJson(json, StudentSubjectsResponseDto::class.java),
            gson.fromJson(json, ActivityPreparationPreviewResponseDto::class.java),
            gson.fromJson(json, ActivityStartResponseDto::class.java),
        ).forEach { response ->
            val success = response.javaClass.getMethod("getSuccess").invoke(response) as Boolean
            assertFalse(success)
        }
    }

    @Test
    fun `base URL requires HTTPS and a trailing slash`() {
        assertEquals("https://example.invalid/", MasaryNetwork.validateBaseUrl("https://example.invalid/").toString())
        assertThrows(IllegalArgumentException::class.java) {
            MasaryNetwork.validateBaseUrl("http://example.invalid/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MasaryNetwork.validateBaseUrl("https://example.invalid")
        }
    }

    private fun <T> parse(name: String, type: Class<T>): T = gson.fromJson(resource(name), type)

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)).readText()
}
