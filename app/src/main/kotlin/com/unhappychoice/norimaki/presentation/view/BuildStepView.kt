package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.text.Html
import android.util.AttributeSet
import android.view.View
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.databinding.BuildStepViewBinding
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import io.reactivex.rxkotlin.addTo
import org.kodein.di.instance

class BuildStepView(context: Context, attr: AttributeSet) : BaseView<BuildStepView>(context, attr) {
    override val presenter: BuildStepPresenter by instance()

    private val binding by lazy {
        BuildStepViewBinding.bind(this)
    }

    private val logSearch by lazy { BuildLogSearch(binding) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)
        logSearch.bind(bag)

        presenter.logSearchAvailable.asObservable()
            .subscribeOnIoObserveOnUI()
            .subscribeNext(logSearch::setAvailable)
            .addTo(bag)

        binding.retryStepLoad.setOnClickListener { presenter.retryStepLoad() }
        presenter.canRetryStepLoad.asObservable()
            .subscribeOnIoObserveOnUI()
            .subscribeNext { retry -> binding.retryStepLoad.visibility = if (retry) View.VISIBLE else View.GONE }
            .addTo(bag)

        presenter.logString.asObservable()
            .subscribeOnIoObserveOnUI()
            .subscribeNext { logSearch.render(Html.fromHtml(it)) }
            .addTo(bag)

        presenter.logLoadFailed.asObservable()
            .subscribeOnIoObserveOnUI()
            .subscribeNext { failed -> binding.logLoadError.visibility = if (failed) View.VISIBLE else View.GONE }
            .addTo(bag)
    }

    internal fun showStep(step: BuildStep) {
        updateScreenTitle(presenter.activity, step.name)
    }

    override fun onDetachedFromWindow() {
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }
}
