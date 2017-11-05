package com.unhappychoice.norimaki.di.module

import com.github.unhappychoice.circleci.CircleCIAPIClient
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.NorimakiApplication
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference
import com.unhappychoice.norimaki.infrastructure.pusher.PusherService
import dagger.Provides
import javax.inject.Singleton

@dagger.Module
class ApplicationModule(val application: NorimakiApplication) {
    @Provides @Singleton fun provideApplication() = application
    @Provides @Singleton fun provideEventBusService() = eventBus
    @Provides @Singleton fun providePusherService() = PusherService(eventBus, gson)
    @Provides @Singleton fun provideGson() = gson
    @Provides fun provideApiService() = CircleCIAPIClient(APITokenPreference(application).token).client()

    private val eventBus = EventBusService()
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()
}