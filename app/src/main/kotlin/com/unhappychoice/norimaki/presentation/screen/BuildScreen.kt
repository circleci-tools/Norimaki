package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.CircleCIAPIClient
import com.github.unhappychoice.circleci.response.Build
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.BuildView
import com.unhappychoice.norimaki.scope.ViewScope
import dagger.Provides
import dagger.Subcomponent
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.PublishSubject
import mortar.MortarScope
import mortar.ViewPresenter
import javax.inject.Inject

class BuildScreen(val build: Build) : Screen() {
  override fun getLayoutResource() = R.layout.build_view
  override fun getSubComponent(activityComponent: ActivityComponent) = activityComponent.buildScreenComponent(Module())

  @Subcomponent(modules = arrayOf(Module::class)) @ViewScope interface Component {
    fun inject(view: BuildView)
  }

  @dagger.Module
  inner class Module {
    @Provides @ViewScope fun provideBuild() = build
  }

  @ViewScope class Presenter @Inject constructor() : ViewPresenter<BuildView>() {
    @Inject lateinit var activity: MainActivity
    @Inject lateinit var build: Build

    val buildSubject = PublishSubject.create<Build>()

    private val token by lazy { APITokenPreference(activity).token }
    private val api by lazy { CircleCIAPIClient(token) }
    private val bag = CompositeDisposable()

    override fun onEnterScope(scope: MortarScope?) {
      super.onEnterScope(scope)
      getBuild()
    }

    override fun onExitScope() {
      bag.dispose()
      super.onExitScope()
    }

    fun getBuild() {
      api.client().getBuild("unhappychoice", build.reponame!!, build.buildNum!!)
        .subscribeOnIoObserveOnUI()
        .withLog()
        .bindTo(buildSubject)
        .addTo(bag)
    }
  }
}