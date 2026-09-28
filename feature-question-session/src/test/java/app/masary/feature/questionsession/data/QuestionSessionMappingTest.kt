package app.masary.feature.questionsession.data

import app.masary.core.network.question.QuestionConfirmedDeltaDto
import app.masary.core.network.question.QuestionConfirmedProfileDeltaDto
import app.masary.core.network.question.QuestionConfirmedSubjectDeltaDto
import app.masary.core.network.question.QuestionFinishResultDto
import app.masary.core.network.question.QuestionSessionDescriptorDto
import app.masary.core.network.question.QuestionSessionScoreDto
import app.masary.core.network.question.QuestionSessionPackageDataDto
import app.masary.core.network.question.QuestionSessionProgressDto
import app.masary.core.network.question.QuestionSessionQuestionDto
import app.masary.feature.questionsession.domain.QuestionPayload
import app.masary.feature.questionsession.domain.QuestionSessionServiceException
import app.masary.feature.questionsession.domain.QuestionType
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionSessionMappingTest {
    @Test
    fun `choose package maps to typed options without answer key`() {
        val payload = JsonParser.parseString(
            """{"options":[{"id":"opt-a-123456789012","text":"1"},{"id":"opt-c-123456789012","text":"4"}]}""",
        ).asJsonObject
        val dto = QuestionSessionPackageDataDto(
            version = "v1",
            generatedAt = "2026-09-28T09:00:00Z",
            session = QuestionSessionDescriptorDto(
                id = "activity-session-001",
                status = "created",
                expiresAt = "2099-01-01 00:00:00",
                subjectVersionId = 12,
                activityType = "choose_test",
                activityMode = "practice",
            ),
            progress = QuestionSessionProgressDto(currentIndex = 0, totalQuestions = 1),
            questions = listOf(
                QuestionSessionQuestionDto(
                    id = "question-opaque-123456",
                    type = "choose",
                    prompt = "ما ناتج 2 + 2؟",
                    payload = payload,
                ),
            ),
        )

        val domain = dto.toDomain()

        assertEquals(QuestionType.Choose, domain.questions.single().type)
        val options = domain.questions.single().payload as QuestionPayload.Options
        assertEquals(listOf("1", "4"), options.options.map { it.text })
        assertTrue(payload.keySet().none { it.contains("answer", ignoreCase = true) })
    }

    @Test
    fun `confirmed student delta maps only for the expected owner`() {
        val dto = QuestionConfirmedDeltaDto(
            available = true,
            reason = "",
            studentId = "42",
            profile = QuestionConfirmedProfileDeltaDto(
                globalXp = 450,
                gems = 12,
                level = 2,
                levelProgressPercent = 50,
            ),
            subjects = listOf(
                QuestionConfirmedSubjectDeltaDto(
                    subjectVersionId = 12,
                    points = 180,
                    level = 2,
                    hearts = 3,
                ),
            ),
            serverVersion = "result-v1",
            confirmedAtEpochMillis = 1_790_000_000_000L,
        )

        val delta = dto.toLocalDelta(expectedStudentId = "42")

        assertEquals("42", delta.studentId)
        assertEquals(450, delta.profile?.globalXp)
        assertEquals(12, delta.profile?.gems)
        assertEquals(180, delta.subjects.single().points)
        assertEquals("result-v1", delta.serverVersion)
        assertThrows(QuestionSessionServiceException::class.java) {
            dto.toLocalDelta(expectedStudentId = "99")
        }
    }

    @Test
    fun `confirmed finish maps server score without local rewards`() {
        val dto = QuestionFinishResultDto(
            sessionId = "activity-session-001",
            status = "completed",
            completedAt = "2026-09-28 12:00:00",
            replayed = false,
            result = QuestionSessionScoreDto(
                correctAnswers = 8,
                incorrectAnswers = 2,
                totalQuestions = 10,
                scorePercent = 80,
            ),
            confirmedDelta = QuestionConfirmedDeltaDto(
                available = false,
                reason = "لم تُثبت المكافآت.",
            ),
        )

        val domain = dto.toDomain()

        assertEquals(8, domain.score.correctAnswers)
        assertEquals(2, domain.score.incorrectAnswers)
        assertEquals(80, domain.score.scorePercent)
        assertEquals(false, domain.confirmedDeltaAvailable)
    }

    @Test
    fun `connect package keeps two independent item lists`() {
        val payload = JsonParser.parseString(
            """{
              "left_items":[
                {"id":"left-1234567890123456","text":"أ"},
                {"id":"left-2234567890123456","text":"ب"}
              ],
              "right_items":[
                {"id":"right-223456789012345","text":"2"},
                {"id":"right-123456789012345","text":"1"}
              ]
            }""",
        ).asJsonObject
        val dto = QuestionSessionPackageDataDto(
            version = "v2",
            generatedAt = "2026-09-28T09:00:00Z",
            session = QuestionSessionDescriptorDto(
                id = "activity-session-002",
                status = "created",
                expiresAt = "2099-01-01 00:00:00",
                subjectVersionId = 12,
                activityType = "connect_test",
                activityMode = "practice",
            ),
            progress = QuestionSessionProgressDto(0, 1),
            questions = listOf(
                QuestionSessionQuestionDto(
                    id = "question-connect-123456",
                    type = "connect",
                    prompt = "صل كل عنصر بما يناسبه.",
                    payload = payload,
                ),
            ),
        )

        val domain = dto.toDomain()
        val connect = domain.questions.single().payload as QuestionPayload.Connect

        assertEquals(2, connect.leftItems.size)
        assertEquals(2, connect.rightItems.size)
        assertEquals("2", connect.rightItems.first().text)
    }
}
