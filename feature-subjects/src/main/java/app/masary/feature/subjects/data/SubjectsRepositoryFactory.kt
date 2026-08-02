package app.masary.feature.subjects.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.masary.core.datastore.SessionManager
import app.masary.core.network.MasaryNetwork
import app.masary.feature.subjects.domain.SubjectsRepository

object SubjectsRepositoryFactory {
    fun create(
        sessionManager: SessionManager,
        baseUrl: String,
        snapshotDataStore: DataStore<Preferences>,
    ): SubjectsRepository = NetworkSubjectsRepository(
        subjectsApi = MasaryNetwork.studentSubjectsApi(baseUrl),
        authApi = MasaryNetwork.studentAuthApi(baseUrl),
        sessionManager = sessionManager,
        snapshotStore = DataStoreSubjectsSnapshotStore(snapshotDataStore),
    )
}
