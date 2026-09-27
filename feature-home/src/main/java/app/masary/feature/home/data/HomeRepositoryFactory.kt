package app.masary.feature.home.data

import app.masary.core.datastore.SessionManager
import app.masary.core.network.MasaryNetwork
import app.masary.core.local.StudentLocalStore
import app.masary.feature.home.domain.HomeRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

object HomeRepositoryFactory {
    fun create(
        sessionManager: SessionManager,
        baseUrl: String,
        snapshotDataStore: DataStore<Preferences>,
        localStore: StudentLocalStore,
    ): HomeRepository = NetworkHomeRepository(
        homeApi = MasaryNetwork.studentHomeApi(baseUrl),
        authApi = MasaryNetwork.studentAuthApi(baseUrl),
        sessionManager = sessionManager,
        liveStateProvider = localStore::observeLiveState,
        snapshotStore = LocalFirstHomeSnapshotStore(
            localStore = localStore,
            legacyStore = DataStoreHomeSnapshotStore(snapshotDataStore),
        ),
    )
}
