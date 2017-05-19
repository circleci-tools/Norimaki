package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.channelName
import com.unhappychoice.norimaki.domain.model.revisionString
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.screen.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.BuildView
import com.unhappychoice.norimaki.scope.ViewScope
import dagger.Provides
import dagger.Subcomponent
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.switchLatest
import io.reactivex.subjects.PublishSubject
import mortar.MortarScope
import javax.inject.Inject

class BuildScreen(val build: Build) : Screen() {
  override fun getLayoutResource() = R.layout.build_view
  override fun getSubComponent(activityComponent: ActivityComponent) = activityComponent.buildScreenComponent(Module())
  override fun getTitle(): String = build.revisionString()

  @Subcomponent(modules = arrayOf(Module::class)) @ViewScope interface Component {
    fun inject(view: BuildView)
  }

  @dagger.Module
  inner class Module {
    @Provides @ViewScope fun provideBuild() = build
  }

  @ViewScope class Presenter @Inject constructor() : PresenterNeedsToken<BuildView>() {
    @Inject lateinit var build: Build

    val buildSubject: PublishSubject<Build> = PublishSubject.create<Build>()

    override fun onEnterScope(scope: MortarScope?) {
      super.onEnterScope(scope)

      pusher.subscribe(build.channelName(), "newAction")
        .subscribeNext { /* TBD */ }
        .addTo(bag)

      pusher.subscribe(build.channelName(), "updateAction")
        .subscribeNext { /* TBD */ }
        .addTo(bag)

      getBuild()
    }

    fun getBuild() {
      api.getBuild(build.username!!, build.reponame!!, build.buildNum!!)
        .subscribeOnIoObserveOnUI()
        .bindTo(buildSubject)
        .addTo(bag)
    }

    fun goToBuildStepScreen(buildStep: BuildStep) {
      if (buildStep.actions.isEmpty()) return
      goTo(activity, BuildStepScreen(build, buildStep))
    }

    fun rebuild() {
      api.retryBuild(build.username!!, build.reponame!!, build.buildNum!!)
        .subscribeOnIoObserveOnUI()
        .subscribeNext { goBack(activity) }
        .addTo(bag)
    }

    fun rebuildWithoutCache() {
      api.deleteCache(build.username!!, build.reponame!!)
        .map {
          api.retryBuild(build.username!!, build.reponame!!, build.buildNum!!).subscribeOnIoObserveOnUI()
        }.switchLatest()
        .subscribeOnIoObserveOnUI()
        .subscribeNext { goBack(activity) }
        .addTo(bag)
    }
  }
}