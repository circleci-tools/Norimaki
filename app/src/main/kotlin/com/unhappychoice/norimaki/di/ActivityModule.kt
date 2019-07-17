package com.unhappychoice.norimaki.di

import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.presentation.presenter.APITokenPresenter
import com.unhappychoice.norimaki.presentation.presenter.BuildListPresenter
import com.unhappychoice.norimaki.presentation.presenter.BuildPresenter
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.provider
import org.kodein.di.generic.singleton

fun activityModule(activity: MainActivity) = Kodein.Module("activity") {
    bind<MainActivity>() with provider { activity }
    bind<APITokenPresenter>() with singleton { APITokenPresenter() }
    bind<BuildListPresenter>() with provider { BuildListPresenter() }
    bind<BuildPresenter>() with provider { BuildPresenter() }
    bind<BuildStepPresenter>() with provider { BuildStepPresenter() }
}
