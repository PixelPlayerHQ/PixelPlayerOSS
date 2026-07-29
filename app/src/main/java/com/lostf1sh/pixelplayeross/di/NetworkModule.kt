package com.lostf1sh.pixelplayeross.di

import com.lostf1sh.pixelplayeross.data.listenbrainz.ListenBrainzApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Base URL is provided in exactly one place so a user-configurable endpoint
     * (self-hosted ListenBrainz / Maloja) stays a trivial follow-up.
     */
    private const val LISTENBRAINZ_BASE_URL = "https://api.listenbrainz.org/"

    @Provides
    @Singleton
    @ListenBrainzRetrofit
    fun provideListenBrainzRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(LISTENBRAINZ_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideListenBrainzApiService(@ListenBrainzRetrofit retrofit: Retrofit): ListenBrainzApiService {
        return retrofit.create(ListenBrainzApiService::class.java)
    }
}
