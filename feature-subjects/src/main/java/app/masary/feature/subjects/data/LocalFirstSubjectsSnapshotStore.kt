package app.masary.feature.subjects.data

import app.masary.core.local.StudentLocalStore
import app.masary.core.local.StudentSubjectStateEntity
import app.masary.feature.subjects.domain.StudentSubjectsData
import app.masary.feature.subjects.domain.SubjectsSnapshotMetadata
import app.masary.feature.subjects.domain.SubjectsSnapshotStore
import com.google.gson.Gson

class LocalFirstSubjectsSnapshotStore(
    private val localStore: StudentLocalStore,
    private val legacyStore: SubjectsSnapshotStore,
    private val gson: Gson = Gson(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : SubjectsSnapshotStore {

    override suspend fun read(studentId: String): StudentSubjectsData? {
        localStore.readDocument(studentId, KIND)?.let { cached ->
            val decoded = runCatching {
                gson.fromJson(cached.payloadJson, StudentSubjectsData::class.java)
            }.getOrNull()
            if (decoded != null && decoded.studentId == studentId && decoded.version.isNotBlank()) {
                return decoded.copy(snapshot = SubjectsSnapshotMetadata(cached.savedAtEpochMillis))
            }
        }

        val legacy = legacyStore.read(studentId) ?: return null
        runCatching { persist(studentId, legacy, legacy.snapshot?.savedAtEpochMillis ?: nowEpochMillis()) }
        return legacy
    }

    override suspend fun write(studentId: String, data: StudentSubjectsData) {
        persist(studentId, data, nowEpochMillis())
    }

    override suspend fun clear() {
        localStore.deleteDocumentKind(KIND)
        legacyStore.clear()
    }

    private suspend fun persist(studentId: String, data: StudentSubjectsData, savedAt: Long) {
        require(data.studentId == studentId) { "Subjects snapshot owner mismatch" }
        localStore.putDocument(
            studentId = studentId,
            kind = KIND,
            payloadJson = gson.toJson(data.copy(snapshot = null)),
            serverVersion = data.version,
            savedAtEpochMillis = savedAt,
        )
        localStore.replaceSubjectStates(
            data.subjects.map { subject ->
                StudentSubjectStateEntity(
                    studentId = studentId,
                    subjectVersionId = subject.subjectVersionId,
                    points = subject.points,
                    level = subject.level,
                    levelProgressPercent = subject.progress.percent.takeIf { subject.progress.available },
                    hearts = subject.hearts,
                    serverVersion = data.version,
                    confirmedAtEpochMillis = savedAt,
                )
            },
        )
    }

    private companion object {
        const val KIND = "subjects"
    }
}
