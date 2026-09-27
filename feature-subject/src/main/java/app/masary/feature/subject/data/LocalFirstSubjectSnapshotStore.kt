package app.masary.feature.subject.data

import app.masary.core.local.StudentLocalStore
import app.masary.core.local.StudentSubjectStateEntity
import app.masary.feature.subject.domain.StudentSubjectPage
import app.masary.feature.subject.domain.SubjectSnapshotMetadata
import app.masary.feature.subject.domain.SubjectSnapshotStore
import com.google.gson.Gson

class LocalFirstSubjectSnapshotStore(
    private val localStore: StudentLocalStore,
    private val legacyStore: SubjectSnapshotStore,
    private val gson: Gson = Gson(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : SubjectSnapshotStore {

    override suspend fun read(studentId: String, subjectVersionId: Int): StudentSubjectPage? {
        localStore.readDocument(studentId, KIND, subjectVersionId.toString())?.let { cached ->
            val decoded = runCatching {
                gson.fromJson(cached.payloadJson, StudentSubjectPage::class.java)
            }.getOrNull()
            if (
                decoded != null &&
                decoded.studentId == studentId &&
                decoded.subjectVersionId == subjectVersionId &&
                decoded.version.isNotBlank()
            ) {
                return decoded.copy(snapshot = SubjectSnapshotMetadata(cached.savedAtEpochMillis))
            }
        }

        val legacy = legacyStore.read(studentId, subjectVersionId) ?: return null
        runCatching {
            persist(
                studentId,
                subjectVersionId,
                legacy,
                legacy.snapshot?.savedAtEpochMillis ?: nowEpochMillis(),
            )
        }
        return legacy
    }

    override suspend fun write(studentId: String, subjectVersionId: Int, data: StudentSubjectPage) {
        persist(studentId, subjectVersionId, data, nowEpochMillis())
    }

    override suspend fun clear() {
        localStore.deleteDocumentKind(KIND)
        legacyStore.clear()
    }

    private suspend fun persist(
        studentId: String,
        subjectVersionId: Int,
        data: StudentSubjectPage,
        savedAt: Long,
    ) {
        require(data.studentId == studentId) { "Subject snapshot owner mismatch" }
        require(data.subjectVersionId == subjectVersionId) { "Subject snapshot id mismatch" }
        localStore.putDocument(
            studentId = studentId,
            kind = KIND,
            documentId = subjectVersionId.toString(),
            payloadJson = gson.toJson(data.copy(snapshot = null)),
            serverVersion = data.version,
            savedAtEpochMillis = savedAt,
        )
        localStore.replaceSubjectStates(
            listOf(
                StudentSubjectStateEntity(
                    studentId = studentId,
                    subjectVersionId = subjectVersionId,
                    points = data.points.value.takeIf { data.points.available },
                    level = data.level.value.takeIf { data.level.available },
                    levelProgressPercent = data.progress.percent.takeIf { data.progress.available },
                    hearts = data.hearts.current,
                    serverVersion = data.version,
                    confirmedAtEpochMillis = savedAt,
                ),
            ),
        )
    }

    private companion object {
        const val KIND = "subject"
    }
}
