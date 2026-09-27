package app.masary.feature.subjects.ui

import app.masary.feature.subjects.domain.StudentSubjectsData
import app.masary.feature.subjects.domain.SubjectsAcademicContext
import app.masary.feature.subjects.domain.SubjectsEmptyState
import app.masary.feature.subjects.domain.SubjectsRepository
import app.masary.feature.subjects.domain.SubjectsSnapshotMetadata
import app.masary.core.models.student.StudentLiveState
import kotlinx.coroutines.flow.flowOf
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
class StudentSubjectsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows snapshot then replaces it with server response`() = runTest(dispatcher) {
        val cached = subjects("cached").copy(snapshot = SubjectsSnapshotMetadata(1L))
        val fresh = subjects("fresh")
        val response = CompletableDeferred<Result<StudentSubjectsData>>()
        val viewModel = StudentSubjectsViewModel(object : SubjectsRepository {
            override fun observeLiveState() = flowOf(StudentLiveState())
            override suspend fun loadSnapshot() = cached
            override suspend fun loadSubjects() = response.await()
            override suspend fun clearSnapshot() = Unit
        })

        runCurrent()
        val cachedState = viewModel.state.value as SubjectsUiState.Content
        assertEquals("cached", cachedState.data.version)
        assertTrue(cachedState.isRefreshing)

        response.complete(Result.success(fresh))
        runCurrent()
        assertEquals("fresh", (viewModel.state.value as SubjectsUiState.Content).data.version)
    }

    @Test
    fun `refresh while request is active does not create a second request`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<StudentSubjectsData>>()
        var calls = 0
        val viewModel = StudentSubjectsViewModel(object : SubjectsRepository {
            override fun observeLiveState() = flowOf(StudentLiveState())
            override suspend fun loadSnapshot(): StudentSubjectsData? = null
            override suspend fun loadSubjects(): Result<StudentSubjectsData> {
                calls += 1
                return response.await()
            }
            override suspend fun clearSnapshot() = Unit
        })

        runCurrent()
        viewModel.refresh()
        runCurrent()
        assertEquals(1, calls)

        response.complete(Result.success(subjects("fresh")))
        runCurrent()
    }

    private fun subjects(version: String) = StudentSubjectsData(
        studentId = "42",
        version = version,
        generatedAt = "now",
        complete = true,
        academic = SubjectsAcademicContext(false, "", "", "", "", "غير متاح"),
        subjects = emptyList(),
        empty = SubjectsEmptyState(true, "لا توجد مواد"),
    )
}
