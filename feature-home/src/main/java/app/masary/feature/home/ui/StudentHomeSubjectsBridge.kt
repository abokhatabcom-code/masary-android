package app.masary.feature.home.ui

import androidx.compose.runtime.Composable
import app.masary.core.models.auth.StudentSession
import app.masary.feature.activitypreparation.ActivityPreparationPendingStore
import app.masary.feature.activitypreparation.ActivityPreparationRepository
import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.notifications.NotificationPermissionState
import app.masary.feature.subjects.domain.SubjectsRepository

/**
 * Phase 09 bridge. The repository is constructed and owned by the authenticated
 * app now, while replacement of the temporary Subjects destination is kept as
 * a focused follow-up change inside the existing phase 08 navigation host.
 */
@Composable
fun StudentHomeRoute(
    session: StudentSession,
    repository: HomeRepository,
    subjectsRepository: SubjectsRepository,
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
    val retainedSubjectsRepository = subjectsRepository
    StudentHomeRoute(
        session = session,
        repository = repository,
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
