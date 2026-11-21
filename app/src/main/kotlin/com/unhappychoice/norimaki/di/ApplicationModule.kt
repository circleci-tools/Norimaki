package com.unhappychoice.norimaki.di

import com.github.unhappychoice.circleci.CircleCIAPIClient
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.NorimakiApplication
import com.unhappychoice.norimaki.domain.service.ArtifactDownloader
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference
import org.kodein.di.*

fun applicationModule(application: NorimakiApplication) = DI.Module("application") {
    bind<NorimakiApplication>() with provider { application }
    bind<EventBusService>() with singleton { EventBusService() }
    bind<ArtifactDownloader>() with singleton { ArtifactDownloader() }
    bind<Gson>() with singleton {
        GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create()
    }
    bind<SessionService>() with singleton {
        val preference = APITokenPreference(application)
        SessionService(
            readToken = { preference.token },
            writeToken = { preference.token = it },
            createClient = { CircleCIAPIClient(it).client1_1() },
            verify = { it.getMe().subscribeOnIoObserveOnUI().firstOrError() }
        )
    }
}
