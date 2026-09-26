package app.masary.feature.trainingcenter.data

import app.masary.core.network.training.StudentTrainingCenterDataDto
import app.masary.core.network.training.TrainingCenterIdentityDto
import app.masary.core.network.training.TrainingCenterToolDto
import app.masary.feature.trainingcenter.domain.TrainingCenterServiceException
import app.masary.feature.trainingcenter.domain.TrainingToolKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrainingCenterMappingTest {
    @Test
    fun `tools are normalized to the stable six-tool order`() {
        val data = StudentTrainingCenterDataDto(
            studentId = "42",
            subjectVersionId = 12,
            generatedAt = "2026-08-03T00:00:00Z",
            identity = TrainingCenterIdentityDto("الرياضيات", "منهج عدن"),
            tools = TrainingToolKey.ordered.reversed().map(::tool),
            version = "v1",
        )

        val mapped = data.toDomain()

        assertEquals(TrainingToolKey.ordered, mapped.tools.map { it.key })
        assertEquals(6, mapped.tools.size)
    }

    @Test
    fun `server cannot change the preparation contract for a stable tool`() {
        val invalid = tool(TrainingToolKey.Choose).copy(activityType = "speed_test")

        assertThrows(TrainingCenterServiceException::class.java) {
            StudentTrainingCenterDataDto(
                studentId = "42",
                subjectVersionId = 12,
                generatedAt = "2026-08-03T00:00:00Z",
                identity = TrainingCenterIdentityDto("الرياضيات", ""),
                tools = listOf(invalid),
                version = "v1",
            ).toDomain()
        }
    }

    @Test
    fun `available tool requires a positive confirmed item count`() {
        val invalid = tool(TrainingToolKey.Fill).copy(itemCount = 0)

        assertThrows(TrainingCenterServiceException::class.java) {
            StudentTrainingCenterDataDto(
                studentId = "42",
                subjectVersionId = 12,
                generatedAt = "2026-08-03T00:00:00Z",
                identity = TrainingCenterIdentityDto("الرياضيات", ""),
                tools = listOf(invalid),
                version = "v1",
            ).toDomain()
        }
    }

    private fun tool(key: TrainingToolKey): TrainingCenterToolDto = TrainingCenterToolDto(
        key = key.wireKey,
        title = key.wireKey,
        description = "وصف مؤكد",
        activityType = key.activityType,
        activityMode = key.activityMode,
        source = key.source,
        available = true,
        status = "ready",
        reason = "",
        itemCount = 3,
    )
}
