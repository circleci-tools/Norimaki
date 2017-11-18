package com.unhappychoice.norimaki.di

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.provider
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.presentation.presenter.APITokenPresenter
import com.unhappychoice.norimaki.presentation.presenter.BuildListPresenter
import com.unhappychoice.norimaki.presentation.presenter.BuildPresenter
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter

fun activityModule(activity: MainActivity) = Kodein.Module {
    bind<MainActivity>() with provider { activity }
    bind<APITokenPresenter>() with provider { APITokenPresenter() }
    bind<BuildListPresenter>() with provider { BuildListPresenter() }
    bind<BuildPresenter>() with provider { BuildPresenter() }
    bind<BuildStepPresenter>() with provider { BuildStepPresenter() }
}
