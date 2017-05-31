package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.text.Html
import android.util.AttributeSet
import android.widget.LinearLayout
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.build_step_view.view.*
import javax.inject.Inject

class BuildStepView(context: Context, attr: AttributeSet) : BaseView(context, attr) {
    @Inject lateinit var presenter: BuildStepPresenter

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)

        presenter.logString.asObservable()
            .subscribeOnIoObserveOnUI()
            .subscribeNext { logText.text = Html.fromHtml(it.replace("\n", "<br>")) }
            .addTo(bag)
    }

    override fun onDetachedFromWindow() {
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }
}