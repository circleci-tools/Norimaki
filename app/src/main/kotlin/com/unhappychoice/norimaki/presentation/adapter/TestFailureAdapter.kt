package com.unhappychoice.norimaki.presentation.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.TestFailureRowBinding
import com.unhappychoice.norimaki.infrastructure.testresults.TestFailure

internal class TestFailureAdapter(initialFailures: List<TestFailure> = emptyList()) :
    RecyclerView.Adapter<TestFailureAdapter.Holder>() {
    private val failures = initialFailures.toMutableList()
    private val expanded = mutableSetOf<Int>()

    override fun getItemCount(): Int = failures.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(TestFailureRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(failures[position], position in expanded) {
            if (!expanded.add(position)) expanded.remove(position)
            position in expanded
        }
    }

    fun append(newFailures: List<TestFailure>) {
        val start = failures.size
        failures.addAll(newFailures)
        notifyItemRangeInserted(start, newFailures.size)
    }

    class Holder(private val binding: TestFailureRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(failure: TestFailure, expanded: Boolean, toggle: () -> Boolean) {
            binding.testClass.text = failure.className
            binding.testClass.isVisible = !failure.className.isNullOrBlank()
            binding.testName.text = failure.name
            binding.message.text = failure.message?.takeIf { it.isNotBlank() }
                ?: binding.root.context.getString(R.string.test_results_no_message)
            showMessage(expanded)
            binding.toggleMessage.setOnClickListener { showMessage(toggle()) }
        }

        private fun showMessage(expanded: Boolean) {
            binding.message.isVisible = expanded
            binding.toggleMessage.setText(
                if (expanded) R.string.test_results_hide_message else R.string.test_results_show_message
            )
        }
    }
}
