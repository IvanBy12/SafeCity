package com.example.safecity.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object ApiClient {

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        .build()

    val api: SafeCityApi by lazy {
        Retrofit.Builder()
            .baseUrl(BackendConfig.BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SafeCityApi::class.java)

    }
}
