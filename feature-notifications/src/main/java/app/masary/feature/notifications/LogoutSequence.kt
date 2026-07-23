package app.masary.feature.notifications
sealed interface TransportOutcome { data object Success:TransportOutcome;data object Unauthorized:TransportOutcome;data object TemporaryFailure:TransportOutcome;data object PermanentFailure:TransportOutcome }
data class RefreshedCredentials(val accessToken:String,val refreshToken:String){override fun toString()="RefreshedCredentials(accessToken=[REDACTED], refreshToken=[REDACTED])"}
sealed interface RefreshOutcome { data class Success(val credentials:RefreshedCredentials):RefreshOutcome;data object TemporaryFailure:RefreshOutcome;data object PermanentFailure:RefreshOutcome }
interface LogoutTransport { suspend fun unregister(accessToken:String):TransportOutcome;suspend fun refresh(refreshToken:String):RefreshOutcome;suspend fun logout(accessToken:String,refreshToken:String):TransportOutcome }
enum class LogoutSequenceResult { Success, Retry, PermanentFailure }
class LogoutSequence(private val transport:LogoutTransport,private val persistRotated:(PendingLogout)->Boolean) {
 suspend fun run(initial:PendingLogout):LogoutSequenceResult {
  var active=initial
  var rotatedCredentialsDurable=true
  when(transport.unregister(active.accessToken)) {
   TransportOutcome.Success -> Unit
   TransportOutcome.Unauthorized -> when(val refreshed=transport.refresh(active.refreshToken)) {
    is RefreshOutcome.Success -> {
     active=active.rotated(refreshed.credentials.accessToken,refreshed.credentials.refreshToken)
     rotatedCredentialsDurable=persistRotated(active)
     when(transport.unregister(active.accessToken)) {
      TransportOutcome.Success->Unit
      TransportOutcome.TemporaryFailure->return if(rotatedCredentialsDurable)LogoutSequenceResult.Retry else LogoutSequenceResult.PermanentFailure
      else->return LogoutSequenceResult.PermanentFailure
     }
    }
    RefreshOutcome.TemporaryFailure->return LogoutSequenceResult.Retry
    RefreshOutcome.PermanentFailure->return LogoutSequenceResult.PermanentFailure
   }
   TransportOutcome.TemporaryFailure->return LogoutSequenceResult.Retry
   TransportOutcome.PermanentFailure->return LogoutSequenceResult.PermanentFailure
  }
  return when(transport.logout(active.accessToken,active.refreshToken)) {
   TransportOutcome.Success->LogoutSequenceResult.Success
   TransportOutcome.TemporaryFailure->if(rotatedCredentialsDurable)LogoutSequenceResult.Retry else LogoutSequenceResult.PermanentFailure
   else->LogoutSequenceResult.PermanentFailure
  }
 }
}
