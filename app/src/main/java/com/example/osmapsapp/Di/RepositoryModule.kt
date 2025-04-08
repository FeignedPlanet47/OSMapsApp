package com.example.osmapsapp.Di

import com.example.osmapsapp.Data.Repository.GeocodingRepositoryImpl
import com.example.osmapsapp.Data.Repository.RoutingRepositoryImpl
import com.example.osmapsapp.Domain.Repository.GeocodingRepository
import com.example.osmapsapp.Domain.Repository.RoutingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGeocodingRepository(
        geocodingRepositoryImpl: GeocodingRepositoryImpl
    ): GeocodingRepository

    @Binds
    @Singleton
    abstract fun bindRoutingRepository(
        routingRepositoryImpl: RoutingRepositoryImpl
    ): RoutingRepository
}