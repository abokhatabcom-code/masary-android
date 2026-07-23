package app.masary.feature.notifications
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
class RegistrationLifecycleTest {
 @Test fun `login register logout and second login rebind same stored token`()=runTest {
  val storage=FakeStorage("encrypted-fcm-token");val remote=FakeRegistrationTransport();val sequence=RegistrationSequence(storage,remote)
  remote.account="student-a";assertEquals(RegistrationSequenceResult.Success,sequence.run());remote.unregister()
  remote.account="student-b";assertEquals(RegistrationSequenceResult.Success,sequence.run())
  assertEquals(listOf("student-a:encrypted-fcm-token","student-b:encrypted-fcm-token"),remote.registrations);assertEquals("encrypted-fcm-token",storage.read())
 }
 @Test fun `token rotation during request converges with retry`()=runTest { val storage=FakeStorage("old");val transport=RegistrationTransport{storage.value="new";TransportOutcome.Success};assertEquals(RegistrationSequenceResult.Retry,RegistrationSequence(storage,transport).run()) }
 @Test fun `temporary and permanent transport failures stay distinct`()=runTest { val storage=FakeStorage("token");assertEquals(RegistrationSequenceResult.Retry,RegistrationSequence(storage){TransportOutcome.TemporaryFailure}.run());assertEquals(RegistrationSequenceResult.PermanentFailure,RegistrationSequence(storage){TransportOutcome.PermanentFailure}.run()) }
 private class FakeStorage(var value:String?):CurrentFcmTokenStore{override fun read()=value}
 private class FakeRegistrationTransport:RegistrationTransport{var account="";val registrations=mutableListOf<String>();override suspend fun register(fcmToken:String?):TransportOutcome{registrations += "$account:$fcmToken";return TransportOutcome.Success};fun unregister()=Unit}
}
