package com.example.projekt_pam.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val credentialsBase64: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Basic $credentialsBase64")
            .build()
        return chain.proceed(request)
    }
}
