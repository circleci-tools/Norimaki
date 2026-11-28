package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.FlakyTestsViewBinding
import com.unhappychoice.norimaki.presentation.adapter.FlakyTestAdapter
import com.unhappychoice.norimaki.presentation.presenter.FlakyTestsPresenter
import com.unhappychoice.norimaki.presentation.presenter.FlakyTestsState
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import org.kodein.di.instance

class FlakyTestsView(context: Context, attr: AttributeSet) : BaseView<FlakyTestsView>(context, attr) {
    override val presenter: FlakyTestsPresenter by instance()
    private val binding by lazy { FlakyTestsViewBinding.bind(this) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.tests.layoutManager = LinearLayoutManager(context)
        binding.retry.setOnClickListener { presenter.load() }
        presenter.takeView(this)
        presenter.render()
    }

    override fun onDetachedFromWindow() {
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }

    internal fun render(state: FlakyTestsState) {
        val tests = (state as? FlakyTestsState.Loaded)?.tests.orEmpty()
        binding.loading.isVisible = state == FlakyTestsState.Loading
        binding.retry.isVisible = state == FlakyTestsState.Failed
        binding.status.isVisible = state == FlakyTestsState.Failed ||
            (state is FlakyTestsState.Loaded && tests.isEmpty())
        binding.status.setText(
            if (state == FlakyTestsState.Failed) R.string.flaky_tests_load_error else R.string.flaky_tests_empty
        )
        binding.tests.isVisible = tests.isNotEmpty()
        binding.tests.adapter = FlakyTestAdapter(tests)
    }
}
