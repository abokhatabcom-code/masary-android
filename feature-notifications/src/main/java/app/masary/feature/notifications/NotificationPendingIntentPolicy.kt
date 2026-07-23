package app.masary.feature.notifications
object NotificationPendingIntentPolicy {
 fun requestCode(messageId:String?,sentTime:Long,destination:String,contentFingerprint:String,localNonce:Long):Int =
  (messageId?.let{"id:$it"} ?: "time:$sentTime|destination:$destination|content:$contentFingerprint|nonce:$localNonce").hashCode()
}
