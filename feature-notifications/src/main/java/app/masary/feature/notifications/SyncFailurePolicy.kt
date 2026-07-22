package app.masary.feature.notifications
object SyncFailurePolicy { fun shouldRetry(httpCode:Int?):Boolean = httpCode == null || httpCode == 408 || httpCode == 429 || httpCode >= 500 }
