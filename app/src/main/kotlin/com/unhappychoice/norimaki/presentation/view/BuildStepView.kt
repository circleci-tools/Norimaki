package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import com.unhappychoice.norimaki.databinding.BuildStepViewBinding
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.adapter.BuildLogAdapter
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import io.reactivex.disposables.Disposable
import org.kodein.di.instance

class BuildStepView(context: Context, attr: AttributeSet) : BaseView<BuildStepView>(context, attr) {
    override val presenter: BuildStepPresenter by instance()

    private val binding by lazy {
        BuildStepViewBinding.bind(this)
    }

    private var logSubscription: Disposable? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)

        val adapter = BuildLogAdapter(presenter.log)
        binding.logLines.layoutManager = LinearLayoutManager(context)
        binding.logLines.itemAnimator = null
        binding.logLines.adapter = adapter
        logSubscription = presenter.log.changes.subscribeNext(adapter::update)
    }

    override fun onDetachedFromWindow() {
        logSubscription?.dispose()
        logSubscription = null
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }
}
