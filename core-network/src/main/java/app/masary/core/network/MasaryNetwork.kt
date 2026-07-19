package app.masary.core.network

import app.masary.core.network.auth.StudentAuthApi
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object MasaryNetwork {
    fun studentAuthApi(baseUrl: HttpUrl, client: OkHttpClient = OkHttpClient()): StudentAuthApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StudentAuthApi::class.java)
}
