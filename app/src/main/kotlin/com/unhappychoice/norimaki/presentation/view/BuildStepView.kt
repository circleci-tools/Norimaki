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
import io.reactivex.rxkotlin.addTo
import org.kodein.di.instance

class BuildStepView(context: Context, attr: AttributeSet) : BaseView<BuildStepView>(context, attr) {
    override val presenter: BuildStepPresenter by instance()

    private val binding by lazy {
        BuildStepViewBinding.bind(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)

        val adapter = BuildLogAdapter(presenter.log)
        binding.logLines.layoutManager = LinearLayoutManager(context)
        binding.logLines.itemAnimator = null
        binding.logLines.adapter = adapter
        val search = BuildLogSearch(binding, presenter.log, adapter)
        search.bind(bag)
        presenter.logSearchAvailable.asObservable().subscribeNext(search::setAvailable).addTo(bag)
        presenter.log.changes.subscribeNext {
            adapter.update(it)
            search.onLogChanged()
        }.addTo(bag)

        binding.retryStepLoad.setOnClickListener { presenter.retryStepLoad() }
        presenter.canRetryStepLoad.asObservable()
            .subscribeNext { retry -> binding.retryStepLoad.visibility = if (retry) View.VISIBLE else View.GONE }
            .addTo(bag)

        presenter.logLoadFailed.asObservable()
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
