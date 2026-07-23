package app.masary.feature.notifications
object NotificationPendingIntentPolicy {
 fun requestCode(messageId:String?,sentTime:Long,destination:String,contentFingerprint:String):Int =
  (messageId?.let{"id:$it"} ?: "time:$sentTime|destination:$destination|content:$contentFingerprint").hashCode()
}
