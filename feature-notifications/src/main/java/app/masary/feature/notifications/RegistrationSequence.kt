package app.masary.feature.notifications
fun interface CurrentFcmTokenStore { fun read():String? }
fun interface RegistrationTransport { suspend fun register(fcmToken:String?):TransportOutcome }
enum class RegistrationSequenceResult { Success, Retry, PermanentFailure }
class RegistrationSequence(private val tokenStore:CurrentFcmTokenStore,private val transport:RegistrationTransport) {
 suspend fun run():RegistrationSequenceResult {
  val snapshot=tokenStore.read()
  return when(transport.register(snapshot)) {
   TransportOutcome.Success->if(tokenStore.read()==snapshot)RegistrationSequenceResult.Success else RegistrationSequenceResult.Retry
   TransportOutcome.TemporaryFailure->RegistrationSequenceResult.Retry
   else->RegistrationSequenceResult.PermanentFailure
  }
 }
}
