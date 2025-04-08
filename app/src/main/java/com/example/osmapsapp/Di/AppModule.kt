package com.example.osmapsapp.Di

import android.content.Context
import android.content.SharedPreferences
import com.example.osmapsapp.Data.Remote.Api.NominatimApi
import com.example.osmapsapp.Data.Remote.Api.OSRMApi
import com.example.osmapsapp.Data.Remote.Api.OverpassApi
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NominatimRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OSRMRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OverpassRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("RouteCachePrefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @NominatimRetrofit
    fun provideNominatimRetrofit(client: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @OSRMRetrofit
    fun provideOSRMRetrofit(client: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://router.project-osrm.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @OverpassRetrofit
    fun provideOverpassRetrofit(client: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://overpass-api.de/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideNominatimApi(@NominatimRetrofit retrofit: Retrofit): NominatimApi {
        return retrofit.create(NominatimApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOSRMApi(@OSRMRetrofit retrofit: Retrofit): OSRMApi {
        return retrofit.create(OSRMApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOverpassApi(@OverpassRetrofit retrofit: Retrofit): OverpassApi {
        return retrofit.create(OverpassApi::class.java)
    }
}