package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildAction
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.channelName
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.screen.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import com.unhappychoice.norimaki.scope.ViewScope
import dagger.Provides
import dagger.Subcomponent
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject

class BuildStepScreen(val build: Build, val buildStep: BuildStep) : Screen() {
  override fun getLayoutResource() = R.layout.build_step_view
  override fun getTitle(): String = buildStep.name

  override fun getSubComponent(activityComponent: ActivityComponent) =
    activityComponent.stepScreenComponent(Module(build, buildStep))

  @Subcomponent(modules = arrayOf(Module::class)) @ViewScope interface Component {
    fun inject(view: BuildStepView)
  }

  @dagger.Module
  class Module(val build: Build, val buildStep: BuildStep) {
    @Provides @ViewScope fun provideBuild() = build
    @Provides @ViewScope fun provideBuildStep() = buildStep
  }

  @ViewScope class Presenter @Inject constructor(val build: Build, val buildStep: BuildStep) : PresenterNeedsToken<BuildStepView>() {
    val logString: Variable<String> = Variable("")

    override fun onEnterScope(scope: MortarScope?) {
      super.onEnterScope(scope)

      pusher.subscribe(build.channelName(), "appendAction")
        .subscribeNext { /* TBD */ }
        .addTo(bag)

      getActions()
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