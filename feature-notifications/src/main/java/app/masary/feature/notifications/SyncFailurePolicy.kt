package app.masary.feature.notifications
import java.io.IOException
enum class SyncDecision { Retry, PermanentFailure }
object SyncFailurePolicy {
 const val MAX_ATTEMPTS=5
 fun classify(httpCode:Int?=null,error:Throwable?=null,attempt:Int=0):SyncDecision {
  if(attempt>=MAX_ATTEMPTS-1)return SyncDecision.PermanentFailure
  if(httpCode!=null)return if(httpCode==408||httpCode==429||httpCode>=500)SyncDecision.Retry else SyncDecision.PermanentFailure
  return if(error is IOException)SyncDecision.Retry else SyncDecision.PermanentFailure
 }
}
