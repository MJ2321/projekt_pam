package com.example.projekt_pam.di

import com.example.projekt_pam.data.remote.AuthInterceptor
import com.example.projekt_pam.data.remote.MovebankApi
import com.example.projekt_pam.data.repository.WildlifeRepositoryImpl
import com.example.projekt_pam.domain.repository.WildlifeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
        // Zastąp "user:password" swoimi danymi do Movebank
        val credentials = "Daniel2137:chujchuj1234"
        val base64 = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        return AuthInterceptor(base64)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
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
