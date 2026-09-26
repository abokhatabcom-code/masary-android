package app.masary.feature.trainingcenter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.masary.core.datastore.SessionManager
import app.masary.core.network.MasaryNetwork
import app.masary.feature.trainingcenter.domain.TrainingCenterRepository

object TrainingCenterRepositoryFactory {
    fun create(
        sessionManager: SessionManager,
        baseUrl: String,
        dataStore: DataStore<Preferences>,
    ): TrainingCenterRepository = NetworkTrainingCenterRepository(
        trainingCenterApi = MasaryNetwork.studentTrainingCenterApi(baseUrl),
        authApi = MasaryNetwork.studentAuthApi(baseUrl),
        sessionManager = sessionManager,
        snapshotStore = DataStoreTrainingCenterSnapshotStore(dataStore),
    )
}
