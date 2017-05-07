package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.response.BuildAction
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.asSequence
import com.unhappychoice.norimaki.extension.bindTo
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.screen.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import com.unhappychoice.norimaki.scope.ViewScope
import dagger.Provides
import dagger.Subcomponent
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject

class BuildStepScreen(val buildStep: BuildStep) : Screen() {
  override fun getLayoutResource() = R.layout.build_step_view

  override fun getSubComponent(activityComponent: ActivityComponent) =
    activityComponent.stepScreenComponent(Module(buildStep))

  @Subcomponent(modules = arrayOf(Module::class)) @ViewScope interface Component {
    fun inject(view: BuildStepView)
  }

  @dagger.Module
  class Module(val buildStep: BuildStep) {
    @Provides @ViewScope fun provideBuildStep() = buildStep
  }

  @ViewScope class Presenter @Inject constructor() : PresenterNeedsToken<BuildStepView>() {
    @Inject lateinit var buildStep: BuildStep
    val logString: Variable<String> = Variable("")

    private val bag = CompositeDisposable()

    override fun onEnterScope(scope: MortarScope?) {
      super.onEnterScope(scope)
      getActions()
    }

    override fun onExitScope() {
      bag.dispose()
      super.onExitScope()
    }

    fun getActions() {
      Observable.concat(buildStep.actions.map { getAction(it) })
        .subscribeOnIoObserveOnUI()
        .bindTo(logString)
        .addTo(bag)
    }

    private fun getAction(action: BuildAction): Observable<String> =
      Observable.create { observer ->
        val client = OkHttpClient()
        val request = Request.Builder().url(action.outputUrl).build()
        val response = client.newCall(request).execute().body().string()
        val out = JSONArray(response).asSequence()
          .map { it.getString("message") }
          .reduce { acc, string -> acc + string }
        observer.onNext(out)
        observer.onComplete()
      }
  }
}