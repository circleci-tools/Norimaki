package com.unhappychoice.norimaki

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.Menu
import com.unhappychoice.norimaki.scope.ActivityScope
import com.unhappychoice.norimaki.screen.Screen
import com.unhappychoice.norimaki.screen.accessToken.APITokenScreen
import dagger.Provides
import flow.*
import kotlinx.android.synthetic.main.activity_main.*
import mortar.MortarScope
import mortar.bundler.BundleServiceRunner
import javax.inject.Inject

class MainActivity : AppCompatActivity() {
  companion object {
    val SCOPE_NAME = "activity_scope"
    val SERVICE_NAME = "activity_component"
  }

  @Inject lateinit var daggerService: DaggerService

  val component: Component by lazy {
    DaggerMainActivity_Component.builder().component(applicationComponent).module(Module()).build()
  }

  override fun getSystemService(name: String?): Any? {
    var scope = MortarScope.findChild(applicationContext, SCOPE_NAME)

    if (scope == null) {
      scope = MortarScope.buildChild(applicationContext)
        .withService(BundleServiceRunner.SERVICE_NAME, BundleServiceRunner())
        .withService(SERVICE_NAME, component)
        .build(SCOPE_NAME)
    }

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
    super.onDestroy()
    MortarScope.findChild(applicationContext, SCOPE_NAME).destroy()
  }

  override fun onSaveInstanceState(outState: Bundle?) {
    super.onSaveInstanceState(outState)
    BundleServiceRunner.getBundleServiceRunner(this).onSaveInstanceState(outState)
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
    override fun changeKey(outgoingState: State?, incomingState: State, direction: Direction, incomingContexts: MutableMap<Any, Context>, callback: TraversalCallback) {
      outgoingState?.let {
        it.save(containerView.getChildAt(0))
        containerView.removeAllViews()
      }

      val screen = incomingState.getKey<Any?>() as? Screen

      screen?.let {
        val incomingView = LayoutInflater.from(this@MainActivity).inflate(it.getLayoutResource(), containerView, false)

        try {
          daggerService.inject(incomingView, this@MainActivity, it)
        } catch (e: Exception) {
          e.printStackTrace()
        }

        containerView.addView(incomingView)
        incomingState.restore(incomingView)
      }
      callback.onTraversalCompleted()
    }
  }

  @dagger.Component(
    dependencies = arrayOf(NorimakiApplication.Component::class),
    modules = arrayOf(Module::class)
  )
  @ActivityScope
  interface Component {
    fun activity(): MainActivity
  }

  @dagger.Module
  inner class Module {
    @Provides @ActivityScope fun provideActivity() = this@MainActivity
  }

  private val applicationComponent by lazy {
    (applicationContext as NorimakiApplication).component.apply { inject(this@MainActivity) }
  }
}

