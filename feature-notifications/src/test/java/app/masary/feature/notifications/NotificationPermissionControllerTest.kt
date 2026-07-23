package app.masary.feature.notifications
import org.junit.Assert.*
import org.junit.Test
class NotificationPermissionControllerTest {
 private val subject=NotificationPermissionController()
 @Test fun `permission is never offered before home`() { assertFalse(subject.shouldOfferExplanation(false,NotificationPermissionState.NotRequested)) }
 @Test fun `all Android permission outcomes are represented`() {
  assertEquals(NotificationPermissionState.NotRequired,subject.state(32,true,false,false)); assertEquals(NotificationPermissionState.SystemDisabled,subject.state(32,false,false,false)); assertEquals(NotificationPermissionState.NotRequested,subject.state(33,false,false,false))
  assertEquals(NotificationPermissionState.Denied,subject.state(33,false,true,true)); assertEquals(NotificationPermissionState.PermanentlyDenied,subject.state(33,false,true,false)); assertEquals(NotificationPermissionState.Granted,subject.state(33,true,true,false))
 }
 @Test fun `denial does not trigger repeated explanation`() { assertFalse(subject.shouldOfferExplanation(true,NotificationPermissionState.Denied)); assertFalse(subject.shouldOfferExplanation(true,NotificationPermissionState.PermanentlyDenied)) }
}
