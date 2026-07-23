package app.masary.feature.notifications
/** One-shot inbox shared by cold-start and warm-start intent delivery. */
class NotificationDestinationInbox {
 private var pending:String?=null
 fun receive(destination:String?) { pending=destination }
 fun consume(hasSession:Boolean,testActive:Boolean,onConsumed:()->Unit={}):String? = pending?.let { NotificationDestinationPolicy.resolve(it,hasSession,testActive) }.also { if(pending!=null)onConsumed(); pending=null }
}
