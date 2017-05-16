package com.unhappychoice.norimaki

import android.support.multidex.MultiDexApplication
import com.github.unhappychoice.circleci.CircleCIAPIClient
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.infrastructure.pusher.PusherService
import com.unhappychoice.norimaki.preference.APITokenPreference
import dagger.Provides
import mortar.MortarScope
import javax.inject.Singleton

class NorimakiApplication : MultiDexApplication() {
  private val scope by lazy {
    MortarScope.buildRootScope()
      .withService(ApplicationComponent.name, component)
      .build("root_scope")
  }

  private val component: ApplicationComponent by lazy {
    DaggerApplicationComponent.builder()
      .module(Module(this))
      .build()
      .apply { inject(this@NorimakiApplication) }
  }

  override fun getSystemService(name: String?): Any? {
    return when (scope.hasService(name)) {
      true -> scope.getService(name)
      false -> super.getSystemService(name)
    }
  }

  @dagger.Module
  class Module(val application: NorimakiApplication) {
    @Provides @Singleton fun provideApplication() = application
    @Provides @Singleton fun provideEventBusService() = eventBus
    @Provides @Singleton fun providePusherService() = PusherService(eventBus)
    @Provides fun provideApiService() = apiService

    private val apiService = CircleCIAPIClient(APITokenPreference(application).token).client()
    private val eventBus = EventBusService()
  }
}

@dagger.Component(modules = arrayOf(NorimakiApplication.Module::class))
@Singleton
interface ApplicationComponent {
  companion object {
    val name = "application_component"
  }

  fun inject(application: NorimakiApplication)
  fun activityComponent(module: MainActivity.Module): ActivityComponent
}

