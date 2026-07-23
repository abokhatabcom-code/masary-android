package app.masary.feature.notifications

enum class NotificationPermissionState { NotRequired, SystemDisabled, NotRequested, Denied, PermanentlyDenied, Granted }

/** Pure decision layer; Android UI owns the actual permission launcher. */
class NotificationPermissionController {
    fun state(sdk: Int, granted: Boolean, requestedBefore: Boolean, canShowRationale: Boolean): NotificationPermissionState = when {
        sdk < 33 && granted -> NotificationPermissionState.NotRequired
        sdk < 33 -> NotificationPermissionState.SystemDisabled
        granted -> NotificationPermissionState.Granted
        !requestedBefore -> NotificationPermissionState.NotRequested
        canShowRationale -> NotificationPermissionState.Denied
        else -> NotificationPermissionState.PermanentlyDenied
    }

    fun shouldOfferExplanation(isHome: Boolean, state: NotificationPermissionState): Boolean =
        isHome && state == NotificationPermissionState.NotRequested
}
