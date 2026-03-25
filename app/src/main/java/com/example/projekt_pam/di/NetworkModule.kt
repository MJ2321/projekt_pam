package com.example.projekt_pam.di

import com.example.projekt_pam.data.remote.AuthInterceptor
import com.example.projekt_pam.data.remote.MovebankApi
import com.example.projekt_pam.data.repository.WildlifeRepositoryImpl
import com.example.projekt_pam.domain.repository.WildlifeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton
import android.util.Base64

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(): AuthInterceptor {
        val credentials = "daniel2137:chujchuj1234"
        val base64 = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        return AuthInterceptor(base64)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Prosty CookieJar do przechowywania sesji w pamięci RAM
        val cookieJar = object : CookieJar {
            private val cookieStore = mutableMapOf<String, List<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: listOf()
            }
        }

        return OkHttpClient.Builder()
            .cookieJar(cookieJar) // KLUCZOWA ZMIANA: obsługa sesji
            .addInterceptor(authInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 WildlifeTracker")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideMovebankApi(okHttpClient: OkHttpClient): MovebankApi {
        return Retrofit.Builder()
            .baseUrl(MovebankApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MovebankApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWildlifeRepository(api: MovebankApi): WildlifeRepository {
        return WildlifeRepositoryImpl(api)
    }
}
