package com.unhappychoice.norimaki.presentation.presenter.core

import android.view.View
import com.github.salomonbrys.kodein.KodeinInjected
import com.github.salomonbrys.kodein.KodeinInjector
import mortar.ViewPresenter

abstract class Presenter<T : View> : ViewPresenter<T>(), KodeinInjected {
    override val injector = KodeinInjector()
}
