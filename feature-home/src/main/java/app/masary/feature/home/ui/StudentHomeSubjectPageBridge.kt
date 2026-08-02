package app.masary.feature.home.ui

import androidx.compose.runtime.Composable
import app.masary.core.models.auth.StudentSession
import app.masary.feature.activitypreparation.ActivityPreparationPendingStore
import app.masary.feature.activitypreparation.ActivityPreparationRepository
import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.notifications.NotificationPermissionState
import app.masary.feature.subject.domain.SubjectRepository
import app.masary.feature.subjects.domain.SubjectsRepository

/**
 * Phase 10 dependency bridge. It keeps app ownership and logout cleanup wired
 * while replacement of SubjectDetails in the existing navigation host remains
 * an isolated, reviewable commit.
 */
@Composable
fun StudentHomeRoute(
    session: StudentSession,
    repository: HomeRepository,
    subjectsRepository: SubjectsRepository,
    subjectRepository: SubjectRepository,
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
    val retainedSubjectRepository = subjectRepository
    StudentHomeRoute(
        session = session,
        repository = repository,
        subjectsRepository = subjectsRepository,
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
