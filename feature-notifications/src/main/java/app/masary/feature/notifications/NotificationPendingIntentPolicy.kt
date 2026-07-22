package app.masary.feature.notifications
object NotificationPendingIntentPolicy {
 fun requestCode(messageId:String?,destination:String):Int = (messageId ?: "destination:$destination").hashCode()
}
