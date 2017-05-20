package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.addAction
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

  @ViewScope class Presenter @Inject constructor(val build: Build) : PresenterNeedsToken<BuildView>() {
    val steps = Variable<List<BuildStep>>(listOf())

    override fun onEnterScope(scope: MortarScope?) {
      super.onEnterScope(scope)

      pusher.newActionEvents(build)
        .map { BuildStep(name = it.log.name, actions = listOf(it.log.toBuildAction())) }
        .filterNotNull()
        .subscribeNext { steps.value = steps.value + it }
        .addTo(bag)

      pusher.updateActionEvents(build)
        .map { it.log.toBuildAction() }
        .withLog("updateAction")
        .subscribeNext { steps.value = steps.value.addAction(it) }
        .addTo(bag)

      getBuild()
    }

    fun getBuild() {
      api.getBuild(build.username!!, build.reponame!!, build.buildNum!!)
        .subscribeOnIoObserveOnUI()
        .subscribeNext { steps.value = steps.value + (it.steps ?: listOf()) }
        .addTo(bag)
    }

    fun goToBuildStepScreen(buildStep: BuildStep) {
      if (buildStep.actions.isEmpty()) return
      val stepIndex = steps.value.indexOf(buildStep)
      goTo(activity, BuildStepScreen(build, buildStep, stepIndex))
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