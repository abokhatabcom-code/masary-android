package app.masary.core.network.notifications
import org.junit.Assert.*
import org.junit.Test
class StudentPushTokenApiTest { @Test fun `request uses random installation identity and Android platform`() { val request=PushInstallationRequest("123e4567-e89b-42d3-a456-426614174000","x".repeat(32),"1.0",1,locale="ar-YE",timezone="Asia/Aden",permissionStatus="Granted"); assertEquals("android",request.platform); assertFalse(request.installationId.contains(request.fcmToken!!)) } }
