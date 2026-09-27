package app.masary.feature.home.ui

import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.home.domain.HomeSnapshotMetadata
import app.masary.feature.home.domain.HomeStudent
import app.masary.feature.home.domain.StudentHomeData
import app.masary.core.models.student.StudentLiveState
import kotlinx.coroutines.flow.flowOf
import com.google.gson.Gson
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
class StudentHomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `shows snapshot first then replaces it with background response`() = runTest(dispatcher) {
        val snapshot = home("cached").copy(snapshot = HomeSnapshotMetadata(1))
        val fresh = home("fresh")
        val response = CompletableDeferred<Result<StudentHomeData>>()
        val viewModel = StudentHomeViewModel(object : HomeRepository {
            override fun observeLiveState() = flowOf(StudentLiveState())
            override suspend fun loadSnapshot() = snapshot
            override suspend fun loadHome() = response.await()
            override suspend fun clearSnapshot() = Unit
        })

        runCurrent()
        val cachedState = viewModel.state.value as HomeUiState.Content
        assertEquals("cached", cachedState.data.student.id)
        assertTrue(cachedState.isRefreshing)

        response.complete(Result.success(fresh))
        runCurrent()
        assertEquals("fresh", (viewModel.state.value as HomeUiState.Content).data.student.id)
    }

    private fun home(id: String): StudentHomeData {
        val json = requireNotNull(javaClass.classLoader?.getResource("home-snapshot.json")).readText()
        return Gson().fromJson(json, StudentHomeData::class.java)
            .copy(student = HomeStudent(id, "student", "طالب", null))
    }
}
