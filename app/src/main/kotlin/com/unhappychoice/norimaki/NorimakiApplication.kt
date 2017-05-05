package com.unhappychoice.norimaki

import android.support.multidex.MultiDexApplication
import dagger.Provides
import mortar.MortarScope
import javax.inject.Singleton

class NorimakiApplication : MultiDexApplication() {
  override fun getSystemService(name: String?): Any? {
    return when (scope.hasService(name)) {
      true -> scope.getService(name)
      false -> super.getSystemService(name)
    }
  }

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

  @dagger.Module
  class Module(val application: NorimakiApplication) {
    @Provides @Singleton fun provideApplication() = application
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

