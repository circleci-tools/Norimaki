package com.unhappychoice.norimaki.presentation.view.core

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.github.salomonbrys.kodein.KodeinInjected
import com.github.salomonbrys.kodein.KodeinInjector
import com.unhappychoice.norimaki.presentation.presenter.core.Presenter
import io.reactivex.disposables.CompositeDisposable

abstract class BaseView<Self: BaseView<Self>>(
    context: Context?,
    attr: AttributeSet?
) : LinearLayout(context, attr), KodeinInjected {
    override val injector = KodeinInjector()

    abstract val presenter: Presenter<Self>

    protected val bag = CompositeDisposable()

    override fun onDetachedFromWindow() {
        bag.dispose()
        super.onDetachedFromWindow()
    }
}
