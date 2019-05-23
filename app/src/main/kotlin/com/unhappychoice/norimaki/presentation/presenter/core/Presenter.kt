package com.unhappychoice.norimaki.presentation.presenter.core

import android.view.View
import mortar.ViewPresenter
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware

abstract class Presenter<T : View> : ViewPresenter<T>(), KodeinAware {
    override lateinit var kodein: Kodein
}
