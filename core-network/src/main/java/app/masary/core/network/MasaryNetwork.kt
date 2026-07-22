package app.masary.core.network

import app.masary.core.network.auth.StudentAuthApi
import app.masary.core.network.home.StudentHomeApi
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object MasaryNetwork {
    fun studentAuthApi(
        baseUrl: String,
        client: OkHttpClient = defaultClient(),
    ): StudentAuthApi = studentAuthApi(validateBaseUrl(baseUrl), client)

    fun studentAuthApi(baseUrl: HttpUrl, client: OkHttpClient = defaultClient()): StudentAuthApi =
        retrofit(baseUrl, client).create(StudentAuthApi::class.java)

    fun studentHomeApi(
        baseUrl: String,
        client: OkHttpClient = defaultClient(),
    ): StudentHomeApi = studentHomeApi(validateBaseUrl(baseUrl), client)

    fun studentHomeApi(baseUrl: HttpUrl, client: OkHttpClient = defaultClient()): StudentHomeApi =
        retrofit(baseUrl, client).create(StudentHomeApi::class.java)

    private fun retrofit(baseUrl: HttpUrl, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    internal fun validateBaseUrl(baseUrl: String): HttpUrl {
        require(baseUrl.endsWith('/')) { "API Base URL must end with /" }
        return baseUrl.toHttpUrl().also {
            require(it.isHttps) { "API Base URL must use HTTPS" }
        }
    }

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
