package app.masary.feature.notifications
class LogoutPreparation(private val persist:(String)->Boolean) { fun save(payload:PendingLogout):Boolean=persist(com.google.gson.Gson().toJson(payload)) }
