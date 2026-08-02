package app.masary.feature.subject.ui

import app.masary.feature.subject.domain.StudentSubjectPage
import app.masary.feature.subject.domain.SubjectAccess
import app.masary.feature.subject.domain.SubjectAccessStatus
import app.masary.feature.subject.domain.SubjectActionAvailability
import app.masary.feature.subject.domain.SubjectActions
import app.masary.feature.subject.domain.SubjectContentSummary
import app.masary.feature.subject.domain.SubjectHearts
import app.masary.feature.subject.domain.SubjectIdentity
import app.masary.feature.subject.domain.SubjectIntValue
import app.masary.feature.subject.domain.SubjectLastActivity
import app.masary.feature.subject.domain.SubjectMedia
import app.masary.feature.subject.domain.SubjectPageNotFoundException
import app.masary.feature.subject.domain.SubjectProgressValue
import app.masary.feature.subject.domain.SubjectRepository
import app.masary.feature.subject.domain.SubjectRestoreTime
import app.masary.feature.subject.domain.SubjectSnapshotMetadata
import app.masary.feature.subject.domain.SubjectStructureMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudentSubjectViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows matching snapshot then replaces it with server response`() = runTest(dispatcher) {
        val cached = subject("cached").copy(snapshot = SubjectSnapshotMetadata(1L))
        val fresh = subject("fresh")
        val response = CompletableDeferred<Result<StudentSubjectPage>>()
        val viewModel = StudentSubjectViewModel(12, object : SubjectRepository {
            override suspend fun loadSnapshot(subjectVersionId: Int) = cached
            override suspend fun loadSubject(subjectVersionId: Int) = response.await()
            override suspend fun clearSnapshots() = Unit
        })

        runCurrent()
        val cachedState = viewModel.state.value as SubjectUiState.Content
        assertEquals("cached", cachedState.data.version)
        assertTrue(cachedState.isRefreshing)

        response.complete(Result.success(fresh))
        runCurrent()
        assertEquals("fresh", (viewModel.state.value as SubjectUiState.Content).data.version)
    }

    @Test
    fun `refresh during active request does not duplicate network call`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<StudentSubjectPage>>()
        var calls = 0
        val viewModel = StudentSubjectViewModel(12, object : SubjectRepository {
            override suspend fun loadSnapshot(subjectVersionId: Int): StudentSubjectPage? = null
            override suspend fun loadSubject(subjectVersionId: Int): Result<StudentSubjectPage> {
                calls += 1
                return response.await()
            }
            override suspend fun clearSnapshots() = Unit
        })

        runCurrent()
        viewModel.refresh()
        runCurrent()
        assertEquals(1, calls)
        response.complete(Result.success(subject("fresh")))
        runCurrent()
    }

    @Test
    fun `not found failure becomes protected not found state`() = runTest(dispatcher) {
        val viewModel = StudentSubjectViewModel(12, object : SubjectRepository {
            override suspend fun loadSnapshot(subjectVersionId: Int): StudentSubjectPage? = null
            override suspend fun loadSubject(subjectVersionId: Int) =
                Result.failure<StudentSubjectPage>(SubjectPageNotFoundException())
            override suspend fun clearSnapshots() = Unit
        })

        runCurrent()
        assertTrue(viewModel.state.value is SubjectUiState.NotFound)
    }

    @Test
    fun `invalid route id does not call repository`() = runTest(dispatcher) {
        var calls = 0
        val viewModel = StudentSubjectViewModel(0, object : SubjectRepository {
            override suspend fun loadSnapshot(subjectVersionId: Int): StudentSubjectPage? {
                calls += 1
                return null
            }
            override suspend fun loadSubject(subjectVersionId: Int): Result<StudentSubjectPage> {
                calls += 1
                return Result.success(subject("fresh"))
            }
            override suspend fun clearSnapshots() = Unit
        })

        runCurrent()
        assertEquals(0, calls)
        assertTrue(viewModel.state.value is SubjectUiState.NotFound)
    }

    private fun subject(version: String) = StudentSubjectPage(
        studentId = "42",
        subjectVersionId = 12,
        generatedAt = "now",
        identity = SubjectIdentity("الرياضيات", "منهج عدن", "gov", SubjectMedia(false, null, "")),
        points = SubjectIntValue(true, 120, ""),
        level = SubjectIntValue(false, null, "غير متاح"),
        progress = SubjectProgressValue(false, null, "غير متاح"),
        hearts = SubjectHearts(2, 3, SubjectRestoreTime(false, null, "غير متاح")),
        access = SubjectAccess(false, SubjectAccessStatus.Unknown, "غير متاح"),
        content = SubjectContentSummary(SubjectStructureMode.Units, false, emptyList(), false, "لاحقًا"),
        lastActivity = SubjectLastActivity(false, null, "", "", SubjectActionAvailability(false, ""), ""),
        actions = SubjectActions(SubjectActionAvailability(false, "لاحقًا")),
        version = version,
    )
}
