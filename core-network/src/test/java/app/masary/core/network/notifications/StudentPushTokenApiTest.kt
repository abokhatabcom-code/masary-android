package app.masary.core.network.notifications
import org.junit.Assert.assertEquals
import org.junit.Test
class StudentPushTokenApiTest { @Test fun `request is pinned to Android student app`() { val request=PushTokenRequest("x".repeat(32)); assertEquals("android",request.platform); assertEquals("student",request.app) } }
