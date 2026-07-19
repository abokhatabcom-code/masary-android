package app.masary.feature.auth.data

import app.masary.feature.auth.domain.AuthRepository

object AuthRepositoryFactory {
    fun create(): AuthRepository = AuthRepository { _, password, _ ->
        password.fill('\u0000')
        Result.failure(IllegalStateException("المصادقة غير متاحة"))
    }
}
