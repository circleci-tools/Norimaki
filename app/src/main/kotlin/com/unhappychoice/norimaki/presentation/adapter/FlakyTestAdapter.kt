package com.unhappychoice.norimaki.presentation.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.FlakyTestRowBinding
import com.unhappychoice.norimaki.infrastructure.flakytests.FlakyTestSummary

internal class FlakyTestAdapter(private val tests: List<FlakyTestSummary> = emptyList()) :
    RecyclerView.Adapter<FlakyTestAdapter.Holder>() {
    override fun getItemCount(): Int = tests.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(FlakyTestRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(tests[position])

    class Holder(private val binding: FlakyTestRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(test: FlakyTestSummary) {
            binding.testClass.text = test.className
            binding.testClass.isVisible = !test.className.isNullOrBlank()
            binding.testName.text = test.testName
            binding.occurrences.text = binding.root.context.getString(R.string.flaky_tests_occurrences, test.occurrences)
        }
    }
}
