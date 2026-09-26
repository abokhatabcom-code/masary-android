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
import app.masary.core.network.subject.StudentSubjectDetailResponseDto
import app.masary.core.network.subjects.StudentSubjectsResponseDto
import app.masary.core.network.training.StudentTrainingCenterResponseDto
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
        assertEquals(120, home.data?.subjects?.single()?.points)
        assertEquals(2, home.data?.subjects?.single()?.level)
        assertEquals(20, home.data?.subjects?.single()?.progressPercent)
        assertEquals("news", home.data?.spotlight?.type)

        val subjects = parse("subjects-success.json", StudentSubjectsResponseDto::class.java)
        assertTrue(subjects.success)
        assertEquals("42", subjects.data?.studentId)
        assertEquals("منهج عدن", subjects.data?.academic?.curriculumName)
        assertEquals("الرياضيات", subjects.data?.subjects?.single()?.name)
        assertEquals(120, subjects.data?.subjects?.single()?.points)
        assertEquals(2, subjects.data?.subjects?.single()?.level)
        assertTrue(subjects.data?.subjects?.single()?.progress?.available == true)
        assertEquals(20, subjects.data?.subjects?.single()?.progress?.percent)
        assertEquals("available", subjects.data?.subjects?.single()?.access?.status)
        assertEquals(null, subjects.data?.subjects?.single()?.media?.key)
        assertTrue(subjects.data?.subjects?.single()?.lastActivity?.available == true)

        val subject = parse("subject-success.json", StudentSubjectDetailResponseDto::class.java)
        assertTrue(subject.success)
        assertEquals("42", subject.data?.studentId)
        assertEquals(12, subject.data?.subjectVersionId)
        assertEquals("الرياضيات", subject.data?.identity?.name)
        assertEquals(120, subject.data?.points?.value)
        assertTrue(subject.data?.level?.available == true)
        assertEquals(2, subject.data?.level?.value)
        assertTrue(subject.data?.progress?.available == true)
        assertEquals(20, subject.data?.progress?.percent)
        assertEquals(2, subject.data?.hearts?.current)
        assertEquals(2, subject.data?.content?.parts?.size)
        assertTrue(subject.data?.content?.detailsAvailable == true)
        assertEquals(1, subject.data?.content?.units?.size)
        assertEquals("الوحدة الأولى", subject.data?.content?.units?.first()?.title)
        assertEquals(2, subject.data?.content?.units?.first()?.lessons?.size)
        assertEquals("in_progress", subject.data?.content?.units?.first()?.state?.status)
        assertEquals(12.0, subject.data?.content?.units?.first()?.progress?.reviewPercent)
        assertEquals("ready", subject.data?.content?.units?.first()?.lessons?.get(1)?.state?.status)
        assertTrue(subject.data?.content?.units?.first()?.lessons?.get(1)?.preparation?.available == true)
        assertEquals("unit", subject.data?.content?.progressSettings?.progressMode)
        assertEquals(30.0, subject.data?.content?.progressSettings?.unlockThresholdPercent)
        assertEquals(1001, subject.data?.lastActivity?.lessonId)
        assertFalse(subject.data?.lastActivity?.preparation?.available == true)

        val trainingCenter = parse(
            "training-center-success.json",
            StudentTrainingCenterResponseDto::class.java,
        )
        assertTrue(trainingCenter.success)
        assertEquals("1001", trainingCenter.data?.studentId)
        assertEquals(41, trainingCenter.data?.subjectVersionId)
        assertEquals(6, trainingCenter.data?.tools?.size)
        assertEquals("choose", trainingCenter.data?.tools?.first()?.key)
        assertEquals(120, trainingCenter.data?.tools?.first()?.itemCount)
        assertEquals("smart_review", trainingCenter.data?.tools?.last()?.key)
        assertFalse(trainingCenter.data?.tools?.last()?.available == true)

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
            gson.fromJson(json, StudentSubjectDetailResponseDto::class.java),
            gson.fromJson(json, StudentTrainingCenterResponseDto::class.java),
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
