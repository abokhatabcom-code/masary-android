package app.masary.feature.home.ui

import androidx.compose.runtime.Composable
import app.masary.core.models.auth.StudentSession
import app.masary.feature.activitypreparation.ActivityPreparationPendingStore
import app.masary.feature.activitypreparation.ActivityPreparationRepository
import app.masary.feature.auth.domain.AuthRepository
import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.notifications.NotificationPermissionState
import app.masary.feature.subject.domain.SubjectRepository
import app.masary.feature.subjects.domain.SubjectsRepository
import app.masary.feature.trainingcenter.domain.TrainingCenterRepository

/**
 * Phase 11 dependency bridge.
 *
 * The repository is created and cleared with the authenticated application lifecycle now. The
 * navigation graph will consume it when the training-tool preparation contract is extended, so
 * the six tools are never routed through an ambiguous generic activity type.
 */
@Composable
fun StudentHomeRoute(
    session: StudentSession,
    repository: HomeRepository,
    subjectsRepository: SubjectsRepository,
    subjectRepository: SubjectRepository,
    trainingCenterRepository: TrainingCenterRepository,
    activityPreparationRepository: ActivityPreparationRepository,
    activityPreparationPendingStore: ActivityPreparationPendingStore,
    onLogout: () -> Unit,
    externalDestination: String? = null,
    onExternalDestinationConsumed: () -> Unit = {},
    permissionState: NotificationPermissionState = NotificationPermissionState.NotRequired,
    onNotificationsPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
) {
    @Suppress("UNUSED_VARIABLE")
    val retainedTrainingCenterRepository = trainingCenterRepository
    StudentHomeRoute(
        session = session,
        repository = repository,
        subjectsRepository = subjectsRepository,
        subjectRepository = subjectRepository,
        activityPreparationRepository = activityPreparationRepository,
        activityPreparationPendingStore = activityPreparationPendingStore,
        onLogout = onLogout,
        externalDestination = externalDestination,
        onExternalDestinationConsumed = onExternalDestinationConsumed,
        permissionState = permissionState,
        onNotificationsPermission = onNotificationsPermission,
        onOpenNotificationSettings = onOpenNotificationSettings,
    )
}
