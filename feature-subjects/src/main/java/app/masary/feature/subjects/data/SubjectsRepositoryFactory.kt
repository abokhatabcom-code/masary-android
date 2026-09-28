package app.masary.feature.subjects.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.masary.core.datastore.SessionManager
import app.masary.core.network.MasaryNetwork
import app.masary.core.local.StudentLocalStore
import app.masary.feature.subjects.domain.SubjectsRepository

object SubjectsRepositoryFactory {
    fun create(
        sessionManager: SessionManager,
        baseUrl: String,
        snapshotDataStore: DataStore<Preferences>,
        localStore: StudentLocalStore,
    ): SubjectsRepository = NetworkSubjectsRepository(
        subjectsApi = MasaryNetwork.studentSubjectsApi(baseUrl),
        authApi = MasaryNetwork.studentAuthApi(baseUrl),
        sessionManager = sessionManager,
        liveStateProvider = localStore::observeLiveState,
        snapshotStore = LocalFirstSubjectsSnapshotStore(
            localStore = localStore,
            legacyStore = DataStoreSubjectsSnapshotStore(snapshotDataStore),
        ),
    )
}
