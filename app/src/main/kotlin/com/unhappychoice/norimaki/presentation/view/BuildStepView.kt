package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.databinding.BuildStepViewBinding
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.adapter.BuildLogAdapter
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import org.kodein.di.instance

class BuildStepView(context: Context, attr: AttributeSet) : BaseView<BuildStepView>(context, attr) {
    override val presenter: BuildStepPresenter by instance()

    private val binding by lazy {
        BuildStepViewBinding.bind(this)
    }

    private val logSubscriptions = CompositeDisposable()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)

        val adapter = BuildLogAdapter(presenter.log)
        binding.logLines.layoutManager = LinearLayoutManager(context)
        binding.logLines.itemAnimator = null
        binding.logLines.adapter = adapter
        presenter.log.changes.subscribeNext(adapter::update).addTo(logSubscriptions)

        binding.retryStepLoad.setOnClickListener { presenter.retryStepLoad() }
        presenter.canRetryStepLoad.asObservable()
            .subscribeNext { retry -> binding.retryStepLoad.visibility = if (retry) View.VISIBLE else View.GONE }
            .addTo(logSubscriptions)

        presenter.logLoadFailed.asObservable()
            .subscribeNext { failed -> binding.logLoadError.visibility = if (failed) View.VISIBLE else View.GONE }
            .addTo(logSubscriptions)
    }

    internal fun showStep(step: BuildStep) {
        updateScreenTitle(presenter.activity, step.name)
    }

    override fun onDetachedFromWindow() {
        logSubscriptions.clear()
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }
}
