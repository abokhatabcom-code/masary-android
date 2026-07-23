package app.masary.feature.home.data

import app.masary.core.datastore.SessionManager
import app.masary.core.network.MasaryNetwork
import app.masary.feature.home.domain.HomeRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

object HomeRepositoryFactory {
    fun create(
        sessionManager: SessionManager,
        baseUrl: String,
        snapshotDataStore: DataStore<Preferences>,
    ): HomeRepository = NetworkHomeRepository(
        homeApi = MasaryNetwork.studentHomeApi(baseUrl),
        authApi = MasaryNetwork.studentAuthApi(baseUrl),
        sessionManager = sessionManager,
        snapshotStore = DataStoreHomeSnapshotStore(snapshotDataStore),
    )
}
