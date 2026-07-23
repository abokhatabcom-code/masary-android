package app.masary.feature.notifications
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
class LogoutSequenceTest {
 @Test fun `expired access rotates persists unregisters and logs out with new refresh`()=runTest { val fake=Fake();var saved:PendingLogout?=null;val result=LogoutSequence(fake){saved=it;true}.run(PendingLogout("old-a","old-r"));assertEquals(LogoutSequenceResult.Success,result);assertEquals(listOf("old-a","new-a"),fake.unregisterAccess);assertEquals("new-r",fake.logoutRefresh);assertEquals("new-r",saved?.refreshToken) }
 @Test fun `temporary failure after refresh keeps newly persisted credentials`()=runTest { val fake=Fake(second=TransportOutcome.TemporaryFailure);var saved:PendingLogout?=null;assertEquals(LogoutSequenceResult.Retry,LogoutSequence(fake){saved=it;true}.run(PendingLogout("old-a","old-r")));assertEquals("new-r",saved?.refreshToken) }
 @Test fun `success false is permanent`()=runTest { val fake=Fake(first=TransportOutcome.PermanentFailure);assertEquals(LogoutSequenceResult.PermanentFailure,LogoutSequence(fake){true}.run(PendingLogout("a","r"))) }
 @Test fun `cancellation is rethrown`()=runTest { val transport=object:LogoutTransport{override suspend fun unregister(accessToken:String):TransportOutcome{throw kotlinx.coroutines.CancellationException()};override suspend fun refresh(refreshToken:String)=RefreshOutcome.PermanentFailure;override suspend fun logout(accessToken:String,refreshToken:String)=TransportOutcome.PermanentFailure};var rethrown=false;try{LogoutSequence(transport){true}.run(PendingLogout("a","r"))}catch(_:kotlinx.coroutines.CancellationException){rethrown=true};assertTrue(rethrown) }
 private class Fake(val first:TransportOutcome=TransportOutcome.Unauthorized,val second:TransportOutcome=TransportOutcome.Success):LogoutTransport {val unregisterAccess=mutableListOf<String>();var logoutRefresh:String?=null;override suspend fun unregister(accessToken:String)=if(unregisterAccess.apply{add(accessToken)}.size==1)first else second;override suspend fun refresh(refreshToken:String)=RefreshOutcome.Success(RefreshedCredentials("new-a","new-r"));override suspend fun logout(accessToken:String,refreshToken:String):TransportOutcome{logoutRefresh=refreshToken;return TransportOutcome.Success}}
}
