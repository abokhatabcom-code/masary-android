package app.masary.core.security

import android.content.Context

object TokenStoreFactory {
    fun create(context: Context): TokenStore = AndroidKeystoreTokenStore(context)
}
