package com.unhappychoice.norimaki

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.Menu
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.core.UseComponent
import com.unhappychoice.norimaki.scope.ActivityScope
import dagger.Provides
import flow.*
import kotlinx.android.synthetic.main.activity_main.*
import mortar.MortarScope
import mortar.bundler.BundleServiceRunner

class MainActivity : AppCompatActivity() {
  private val component: ActivityComponent by lazy {
    (applicationContext.getSystemService(ApplicationComponent.name) as ApplicationComponent)
      .activityComponent(Module(this))
      .apply { inject(this@MainActivity) }
  }

  private val scope: MortarScope by lazy {
    MortarScope.buildChild(applicationContext)
      .withService(BundleServiceRunner.SERVICE_NAME, BundleServiceRunner())
      .withService(ActivityComponent.name, component)
      .build("activity_scope")
  }

  override fun getSystemService(name: String?): Any? {
    return when (scope.hasService(name)) {
      true -> scope.getService(name)
      false -> super.getSystemService(name)
    }
  }

  override fun attachBaseContext(baseContext: Context) {
    super.attachBaseContext(getFlowContext(baseContext))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    BundleServiceRunner.getBundleServiceRunner(this).onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
  }

  override fun onDestroy() {
    scope.destroy()
    super.onDestroy()
  }

  override fun onSaveInstanceState(outState: Bundle?) {
    BundleServiceRunner.getBundleServiceRunner(this).onSaveInstanceState(outState)
    super.onSaveInstanceState(outState)
  }

  override fun onBackPressed() {
    if (!Flow.get(this).goBack()) {
      super.onBackPressed()
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    return true
  }

  private fun getFlowContext(baseContext: Context): Context {
    return Flow.configure(baseContext, this)
      .dispatcher(KeyDispatcher.configure(this, Changer()).build())
      .defaultKey(APITokenScreen())
      .keyParceler(GsonParceler())
      .install()
  }

  private inner class Changer : KeyChanger {
    override fun changeKey(
      outgoingState: State?,
      incomingState: State,
      direction: Direction,
      incomingContexts: MutableMap<Any, Context>,
      callback: TraversalCallback
    ) {
      outgoingState?.save(containerView.getChildAt(0))
      containerView.removeAllViews()

      val screen = incomingState.getKey<Any?>() as? Screen

      screen?.getLayoutResource()?.let {
        val inflater = LayoutInflater.from(this@MainActivity)
        val view = inflater.inflate(it, containerView, false)
        try {
          (view as? UseComponent)?.inject(screen)
        } catch (e: Exception) {
          e.printStackTrace()
        }
        containerView.addView(view)
        incomingState.restore(view)
      }

      callback.onTraversalCompleted()
    }
  }

  @dagger.Module
  class Module(val activity: MainActivity) {
    @Provides @ActivityScope fun provideActivity() = activity
  }
}

@dagger.Subcomponent(modules = arrayOf(MainActivity.Module::class))
@ActivityScope
interface ActivityComponent {
  companion object {
    val name = "activity_component"
  }

  fun inject(activity: MainActivity)
  fun apiTokenScreenComponent(): APITokenScreen.Component
  fun buildListScreenComponent(): BuildListScreen.Component
  fun buildScreenComponent(module: BuildScreen.Module): BuildScreen.Component
}
