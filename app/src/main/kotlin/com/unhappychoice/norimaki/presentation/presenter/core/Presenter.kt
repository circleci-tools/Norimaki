package com.unhappychoice.norimaki.presentation.presenter.core

import android.view.View
import mortar.ViewPresenter
import org.kodein.di.DI
import org.kodein.di.DIAware

abstract class Presenter<T : View> : ViewPresenter<T>(), DIAware {
    override lateinit var di: DI
}
