package com.unhappychoice.norimaki.di

import com.github.unhappychoice.circleci.CircleCIAPIClient
import com.github.unhappychoice.circleci.CircleCIAPIClientV1
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.NorimakiApplication
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference
import com.unhappychoice.norimaki.infrastructure.pusher.PusherService
import org.kodein.di.*

fun applicationModule(application: NorimakiApplication) = DI.Module("application") {
    bind<NorimakiApplication>() with provider { application }
    bind<EventBusService>() with singleton { EventBusService() }
    bind<PusherService>() with singleton { PusherService(instance(), instance()) }
    bind<Gson>() with singleton {
        GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create()
    }
    bind<CircleCIAPIClientV1>() with provider {
        CircleCIAPIClient(APITokenPreference(application).token).client()
    }
}