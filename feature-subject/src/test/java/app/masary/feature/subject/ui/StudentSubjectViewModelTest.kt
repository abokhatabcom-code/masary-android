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
import app.masary.core.models.student.StudentLiveState
import app.masary.core.models.student.StudentSubjectLiveState
import kotlinx.coroutines.flow.MutableStateFlow
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
            override fun observeLiveState() = flowOf(StudentLiveState())
            override suspend fun loadSnapshot(subjectVersionId: Int) = cached
            override suspend fun loadSubject(subjectVersionId: Int) = response.await()
            override suspend fun clearSnapshots() = Unit
        })

        runCurrent()
        val cachedState = viewModel.state.value as SubjectUiState.Content
        assertEquals("cached", cachedState.data.version)
        assertEquals(false, cachedState.isRefreshing)

        response.complete(Result.success(fresh))
        runCurrent()
        assertEquals("fresh", (viewModel.state.value as SubjectUiState.Content).data.version)
    }

    @Test
    fun `mismatched cached subject is never displayed while requested subject loads`() = runTest(dispatcher) {
        val wrongCached = subject("wrong").copy(
            subjectVersionId = 13,
            identity = subject("wrong").identity.copy(name = "التربية الإسلامية"),
        )
        val response = CompletableDeferred<Result<StudentSubjectPage>>()
        val viewModel = StudentSubjectViewModel(
            subjectVersionId = 12,
            repository = object : SubjectRepository {
                override fun observeLiveState() = flowOf(StudentLiveState())
                override suspend fun loadSnapshot(subjectVersionId: Int) = wrongCached
                override suspend fun loadSubject(subjectVersionId: Int) = response.await()
                override suspend fun clearSnapshots() = Unit
            },
            requestedSubjectName = "الأحياء",
        )

        runCurrent()

        val loading = viewModel.state.value as SubjectUiState.Loading
        assertEquals(12, loading.subjectVersionId)
        assertEquals("الأحياء", loading.subjectName)

        response.complete(Result.success(subject("fresh").copy(
            identity = subject("fresh").identity.copy(name = "الأحياء"),
        )))
        runCurrent()

        val content = viewModel.state.value as SubjectUiState.Content
        assertEquals(12, content.data.subjectVersionId)
        assertEquals("الأحياء", content.data.identity.name)
    }

    @Test
    fun `mismatched server response cannot replace correct cached subject`() = runTest(dispatcher) {
        val cached = subject("cached").copy(
            identity = subject("cached").identity.copy(name = "الأحياء"),
            snapshot = SubjectSnapshotMetadata(1L),
        )
        val response = CompletableDeferred<Result<StudentSubjectPage>>()
        val viewModel = StudentSubjectViewModel(12, object : SubjectRepository {
            override fun observeLiveState() = flowOf(StudentLiveState())
            override suspend fun loadSnapshot(subjectVersionId: Int) = cached
            override suspend fun loadSubject(subjectVersionId: Int) = response.await()
            override suspend fun clearSnapshots() = Unit
        })

        runCurrent()
        response.complete(
            Result.success(
                subject("wrong").copy(
                    subjectVersionId = 13,
                    identity = subject("wrong").identity.copy(name = "التربية الإسلامية"),
                ),
            ),
        )
        runCurrent()

        val content = viewModel.state.value as SubjectUiState.Content
        assertEquals(12, content.data.subjectVersionId)
        assertEquals("الأحياء", content.data.identity.name)
    }

    @Test
    fun `refresh during active request does not duplicate network call`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<StudentSubjectPage>>()
        var calls = 0
        val viewModel = StudentSubjectViewModel(12, object : SubjectRepository {
            override fun observeLiveState() = flowOf(StudentLiveState())
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
            override fun observeLiveState() = flowOf(StudentLiveState())
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
            override fun observeLiveState() = flowOf(StudentLiveState())
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

    @Test
    fun `partial subject delta preserves known counters that were not changed`() = runTest(dispatcher) {
        val cached = subject("cached").copy(
            points = SubjectIntValue(true, 120, ""),
            level = SubjectIntValue(true, 3, ""),
            progress = SubjectProgressValue(true, 40, ""),
            snapshot = SubjectSnapshotMetadata(1L),
        )
        val response = CompletableDeferred<Result<StudentSubjectPage>>()
        val live = MutableStateFlow(StudentLiveState())
        val viewModel = StudentSubjectViewModel(12, object : SubjectRepository {
            override fun observeLiveState() = live
            override suspend fun loadSnapshot(subjectVersionId: Int) = cached
            override suspend fun loadSubject(subjectVersionId: Int) = response.await()
            override suspend fun clearSnapshots() = Unit
        })

        runCurrent()
        live.value = StudentLiveState(
            subjects = mapOf(
                12 to StudentSubjectLiveState(
                    studentId = "42",
                    subjectVersionId = 12,
                    points = null,
                    level = null,
                    levelProgressPercent = null,
                    hearts = 1,
                    serverVersion = "confirmed-3",
                    confirmedAtEpochMillis = 3L,
                ),
            ),
        )
        runCurrent()

        val data = (viewModel.state.value as SubjectUiState.Content).data
        assertEquals(120, data.points.value)
        assertEquals(3, data.level.value)
        assertEquals(40, data.progress.percent)
        assertEquals(1, data.hearts.current)

        response.complete(Result.success(cached.copy(snapshot = null)))
        runCurrent()
    }

    @Test
    fun `confirmed live state updates subject points without another network request`() = runTest(dispatcher) {
        val cached = subject("cached").copy(snapshot = SubjectSnapshotMetadata(1L))
        val response = CompletableDeferred<Result<StudentSubjectPage>>()
        val live = MutableStateFlow(StudentLiveState())
        var networkCalls = 0
        val viewModel = StudentSubjectViewModel(12, object : SubjectRepository {
            override fun observeLiveState() = live
            override suspend fun loadSnapshot(subjectVersionId: Int) = cached
            override suspend fun loadSubject(subjectVersionId: Int): Result<StudentSubjectPage> {
                networkCalls += 1
                return response.await()
            }
            override suspend fun clearSnapshots() = Unit
        })

        runCurrent()
        live.value = StudentLiveState(
            subjects = mapOf(
                12 to StudentSubjectLiveState(
                    studentId = "42",
                    subjectVersionId = 12,
                    points = 175,
                    level = 2,
                    levelProgressPercent = 75,
                    hearts = 1,
                    serverVersion = "confirmed-2",
                    confirmedAtEpochMillis = 2L,
                ),
            ),
        )
        runCurrent()

        val state = viewModel.state.value as SubjectUiState.Content
        assertEquals(175, state.data.points.value)
        assertEquals(2, state.data.level.value)
        assertEquals(75, state.data.progress.percent)
        assertEquals(1, state.data.hearts.current)
        assertEquals(1, networkCalls)

        response.complete(Result.success(cached.copy(snapshot = null)))
        runCurrent()
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
