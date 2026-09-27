package app.masary.feature.subject.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.masary.core.datastore.SessionManager
import app.masary.core.network.MasaryNetwork
import app.masary.core.local.StudentLocalStore
import app.masary.feature.subject.domain.SubjectRepository

object SubjectRepositoryFactory {
    fun create(
        sessionManager: SessionManager,
        baseUrl: String,
        dataStore: DataStore<Preferences>,
        localStore: StudentLocalStore,
    ): SubjectRepository = NetworkSubjectRepository(
        subjectApi = MasaryNetwork.studentSubjectApi(baseUrl),
        authApi = MasaryNetwork.studentAuthApi(baseUrl),
        sessionManager = sessionManager,
        snapshotStore = LocalFirstSubjectSnapshotStore(
            localStore = localStore,
            legacyStore = DataStoreSubjectSnapshotStore(dataStore),
        ),
    )
}
