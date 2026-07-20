package app.masary.core.network

import app.masary.core.network.auth.StudentAuthApi
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object MasaryNetwork {
    const val PRODUCTION_BASE_URL = "https://masary.app/"

    fun studentAuthApi(
        baseUrl: String = PRODUCTION_BASE_URL,
        client: OkHttpClient = defaultClient(),
    ): StudentAuthApi = studentAuthApi(baseUrl.toHttpUrl(), client)

    fun studentAuthApi(baseUrl: HttpUrl, client: OkHttpClient = defaultClient()): StudentAuthApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StudentAuthApi::class.java)

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
