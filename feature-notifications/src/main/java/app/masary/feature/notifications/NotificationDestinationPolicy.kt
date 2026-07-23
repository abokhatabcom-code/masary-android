package app.masary.feature.notifications
object NotificationDestinationPolicy {
    private val allowed = setOf("home", "subjects", "ranking", "profile")
    fun resolve(value: String?, hasSession: Boolean, educationalTestActive: Boolean): String =
        if (!hasSession || educationalTestActive || value !in allowed) "home" else requireNotNull(value)
}
