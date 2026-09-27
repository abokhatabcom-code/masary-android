package app.masary.feature.home.data

import app.masary.core.local.StudentLocalStore
import app.masary.core.local.StudentProfileStateEntity
import app.masary.core.local.StudentSubjectStateEntity
import app.masary.feature.home.domain.HomeSnapshotMetadata
import app.masary.feature.home.domain.HomeSnapshotStore
import app.masary.feature.home.domain.StudentHomeData
import com.google.gson.Gson

class LocalFirstHomeSnapshotStore(
    private val localStore: StudentLocalStore,
    private val legacyStore: HomeSnapshotStore,
    private val gson: Gson = Gson(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : HomeSnapshotStore {

    override suspend fun read(studentId: String): StudentHomeData? {
        localStore.readDocument(studentId, KIND)?.let { cached ->
            val decoded = runCatching {
                gson.fromJson(cached.payloadJson, StudentHomeData::class.java)
            }.getOrNull()
            if (decoded != null && decoded.student.id == studentId && decoded.version.isNotBlank()) {
                return decoded.copy(snapshot = HomeSnapshotMetadata(cached.savedAtEpochMillis))
            }
        }

        val legacy = legacyStore.read(studentId) ?: return null
        runCatching { persist(studentId, legacy, legacy.snapshot?.savedAtEpochMillis ?: nowEpochMillis()) }
        return legacy
    }

    override suspend fun write(studentId: String, data: StudentHomeData) {
        persist(studentId, data, nowEpochMillis())
    }

    override suspend fun clear() {
        localStore.deleteDocumentKind(KIND)
        legacyStore.clear()
    }

    private suspend fun persist(studentId: String, data: StudentHomeData, savedAt: Long) {
        require(data.student.id == studentId) { "Home snapshot owner mismatch" }
        localStore.putDocument(
            studentId = studentId,
            kind = KIND,
            payloadJson = gson.toJson(data.copy(snapshot = null)),
            serverVersion = data.version,
            savedAtEpochMillis = savedAt,
        )
        localStore.replaceProfileState(
            StudentProfileStateEntity(
                studentId = studentId,
                globalXp = data.summary.globalXp,
                gems = data.summary.gems,
                level = data.summary.level,
                levelProgressPercent = data.summary.levelPercent,
                levelNextXp = data.summary.levelNextXp,
                streakCurrentDays = data.streak.currentDays,
                unreadNotifications = data.notifications.unreadCount,
                serverVersion = data.version,
                confirmedAtEpochMillis = savedAt,
            ),
        )
        localStore.replaceSubjectStates(
            data.subjects.map { subject ->
                StudentSubjectStateEntity(
                    studentId = studentId,
                    subjectVersionId = subject.subjectVersionId,
                    points = subject.points,
                    level = subject.level,
                    levelProgressPercent = subject.progressPercent,
                    hearts = subject.hearts,
                    serverVersion = data.version,
                    confirmedAtEpochMillis = savedAt,
                )
            },
        )
    }

    private companion object {
        const val KIND = "home"
    }
}
