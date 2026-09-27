package app.masary.feature.home.ui

import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.home.domain.HomeSnapshotMetadata
import app.masary.feature.home.domain.HomeStudent
import app.masary.feature.home.domain.StudentHomeData
import app.masary.core.models.student.StudentLiveState
import app.masary.core.models.student.StudentProfileLiveState
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test fun `confirmed live state updates Home without a second network request`() = runTest(dispatcher) {
        val cached = home("42").copy(snapshot = HomeSnapshotMetadata(1))
        val response = CompletableDeferred<Result<StudentHomeData>>()
        val live = MutableStateFlow(StudentLiveState())
        var networkCalls = 0
        val viewModel = StudentHomeViewModel(object : HomeRepository {
            override fun observeLiveState() = live
            override suspend fun loadSnapshot() = cached
            override suspend fun loadHome(): Result<StudentHomeData> {
                networkCalls += 1
                return response.await()
            }
            override suspend fun clearSnapshot() = Unit
        })

        runCurrent()
        live.value = StudentLiveState(
            profile = StudentProfileLiveState(
                studentId = "42",
                globalXp = 990,
                gems = 17,
                level = 4,
                levelProgressPercent = 30,
                levelNextXp = 1_200,
                todayXp = 45,
                todaySeconds = 600,
                todayMinutes = 10,
                todayAttempts = 3,
                streakCurrentDays = 6,
                unreadNotifications = 2,
                smartGuideCompletedSteps = 2,
                smartGuideTotalSteps = 4,
                smartGuideCompletionPercent = 50,
                serverVersion = "confirmed-2",
                confirmedAtEpochMillis = 2,
            ),
        )
        runCurrent()

        val state = viewModel.state.value as HomeUiState.Content
        assertEquals(990, state.data.summary.globalXp)
        assertEquals(17, state.data.summary.gems)
        assertEquals(45, state.data.today.xp)
        assertEquals(6, state.data.streak.currentDays)
        assertEquals(1, networkCalls)

        response.complete(Result.success(cached.copy(snapshot = null)))
        runCurrent()
    }

    private fun home(id: String): StudentHomeData {
        val json = requireNotNull(javaClass.classLoader?.getResource("home-snapshot.json")).readText()
        return Gson().fromJson(json, StudentHomeData::class.java)
            .copy(student = HomeStudent(id, "student", "طالب", null))
    }
}
