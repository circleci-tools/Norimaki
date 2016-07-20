package com.unhappychoice.norimaki

import android.app.Application
import android.content.Context
import android.support.multidex.MultiDexApplication
import dagger.Provides
import mortar.MortarScope
import javax.inject.Singleton

class NorimakiApplication : MultiDexApplication() {
  companion object {
    val SCOPE_NAME = "root_scope"
  }

  val component: Component by lazy {
    DaggerNorimakiApplication_Component.builder().module(Module()).build()
  }

  override fun getSystemService(name: String?): Any? {
    return when (scope.hasService(name)) {
      true -> scope.getService(name)
      false -> super.getSystemService(name)
    }
  }

  @dagger.Component(modules = arrayOf(Module::class))
  @Singleton
  interface Component {
    fun inject(activity: MainActivity)
    fun application(): NorimakiApplication
    fun daggerService(): DaggerService
  }

  @dagger.Module
  inner class Module {
    @Provides @Singleton fun provideApplication() = this@NorimakiApplication
    @Provides @Singleton fun provideDaggerService() = DaggerService()
  }

  private val scope by lazy { MortarScope.buildRootScope().build(SCOPE_NAME) }
}
