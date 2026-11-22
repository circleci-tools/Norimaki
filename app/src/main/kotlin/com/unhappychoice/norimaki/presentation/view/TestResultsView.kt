package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.TestResultsViewBinding
import com.unhappychoice.norimaki.infrastructure.testresults.TestFailure
import com.unhappychoice.norimaki.presentation.adapter.TestFailureAdapter
import com.unhappychoice.norimaki.presentation.presenter.TestResultsPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import org.kodein.di.instance

class TestResultsView(context: Context, attr: AttributeSet) : BaseView<TestResultsView>(context, attr) {
    override val presenter: TestResultsPresenter by instance()
    private val binding by lazy { TestResultsViewBinding.bind(this) }
    private var receivedPage = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.failures.layoutManager = LinearLayoutManager(context)
        binding.retry.setOnClickListener { presenter.load() }
        showLoading()
        presenter.takeView(this)
    }

    override fun onDetachedFromWindow() {
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }

    internal fun showLoading() {
        receivedPage = false
        binding.failures.adapter = null
        showStatus(R.string.test_results_loading)
    }

    internal fun showUnavailable() = showStatus(R.string.test_results_unavailable)

    internal fun showLoadError() {
        val message = if (receivedPage) R.string.test_results_incomplete else R.string.test_results_load_error
        showStatus(message, retry = true)
    }

    internal fun appendFailures(failures: List<TestFailure>) {
        receivedPage = true
        if (failures.isEmpty()) return
        val adapter = binding.failures.adapter as? TestFailureAdapter
            ?: TestFailureAdapter().also { binding.failures.adapter = it }
        adapter.append(failures)
    }

    internal fun finishLoading() {
        if (binding.failures.adapter == null) showStatus(R.string.test_results_no_failures)
        else binding.status.isVisible = false
    }

    private fun showStatus(message: Int, retry: Boolean = false) {
        binding.status.setText(message)
        binding.status.isVisible = true
        binding.retry.isVisible = retry
    }
}
