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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)
        binding.failures.layoutManager = LinearLayoutManager(context)
        binding.retry.setOnClickListener { load() }
        load()
    }

    override fun onDetachedFromWindow() {
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }

    internal fun showUnavailable() = showStatus(R.string.test_results_unavailable)

    internal fun showLoadError() = showStatus(R.string.test_results_load_error, retry = true)

    internal fun showFailures(failures: List<TestFailure>) {
        if (failures.isEmpty()) {
            showStatus(R.string.test_results_no_failures)
            return
        }
        binding.status.isVisible = false
        binding.failures.adapter = TestFailureAdapter(failures)
    }

    private fun load() {
        showStatus(R.string.test_results_loading)
        presenter.load(this)
    }

    private fun showStatus(message: Int, retry: Boolean = false) {
        binding.failures.adapter = null
        binding.status.setText(message)
        binding.status.isVisible = true
        binding.retry.isVisible = retry
    }
}
