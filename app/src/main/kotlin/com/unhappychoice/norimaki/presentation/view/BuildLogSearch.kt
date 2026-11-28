package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.BuildStepViewBinding
import com.unhappychoice.norimaki.extension.androidMainScheduler
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.adapter.BuildLogAdapter
import com.unhappychoice.norimaki.presentation.log.BuildLog
import com.unhappychoice.norimaki.presentation.log.LogMatch
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables
import io.reactivex.disposables.SerialDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

internal class BuildLogSearch(
    private val binding: BuildStepViewBinding,
    private val log: BuildLog,
    private val adapter: BuildLogAdapter
) {
    private lateinit var search: SerialDisposable
    private var matches = LogMatchIndex.EMPTY
    private var selected = 0

    fun bind(bag: CompositeDisposable) = with(binding) {
        search = SerialDisposable()
        bag.add(search)
        logFind.setOnClickListener { open() }
        logCloseSearch.setOnClickListener {
            close()
            logFind.requestFocus()
        }
        val queryWatcher = logQuery.doAfterTextChanged { refresh() }
        bag.add(Disposables.fromAction { logQuery.removeTextChangedListener(queryWatcher) })
        logPrevious.setOnClickListener { move(-1) }
        logNext.setOnClickListener { move(1) }
        if (logSearchPanel.visibility == View.VISIBLE) refresh()
    }

    fun setAvailable(available: Boolean) {
        if (!available) close()
        binding.logFind.visibility =
            if (available && binding.logSearchPanel.visibility != View.VISIBLE) View.VISIBLE else View.GONE
    }

    fun onLogChanged() {
        if (binding.logSearchPanel.visibility == View.VISIBLE) refresh()
    }

    private fun open() = with(binding) {
        logSearchPanel.visibility = View.VISIBLE
        logFind.visibility = View.GONE
        logQuery.requestFocus()
        keyboard().showSoftInput(logQuery, 0)
        refresh()
    }

    private fun close() = with(binding) {
        if (logSearchPanel.visibility != View.VISIBLE) return@with
        keyboard().hideSoftInputFromWindow(logQuery.windowToken, 0)
        logSearchPanel.visibility = View.GONE
        logFind.visibility = View.VISIBLE
        logQuery.text.clear()
    }

    private fun refresh() = with(binding) {
        search.set(null)
        matches = LogMatchIndex.EMPTY
        updateMatch(null)
        val query = logQuery.text.toString()
        if (query.isEmpty() || logSearchPanel.visibility != View.VISIBLE) return@with

        logMatchCount.setText(R.string.build_log_searching)
        val snapshot = log.snapshot()
        search.set(
            Observable.timer(150, TimeUnit.MILLISECONDS, Schedulers.computation())
                .flatMap { LogMatchIndex.find(snapshot, query) }
                .observeOn(androidMainScheduler())
                .subscribeNext {
                    matches = it
                    selected = 0
                    updateMatch(if (it.size == 0) null else it[0])
                    if (it.size == 0) logMatchCount.setText(R.string.build_log_no_matches)
                }
        )
    }

    private fun move(delta: Int) {
        if (matches.size == 0) return
        selected = (selected + delta + matches.size) % matches.size
        updateMatch(matches[selected])
    }

    private fun updateMatch(match: LogMatch?) = with(binding) {
        logPrevious.isEnabled = matches.size > 1
        logNext.isEnabled = matches.size > 1
        adapter.highlight(match)
        if (match == null) {
            logMatchCount.text = ""
            return@with
        }

        logMatchCount.text = root.context.getString(R.string.build_log_match_count, selected + 1, matches.size)
        val manager = logLines.layoutManager as LinearLayoutManager
        if (match.line !in manager.findFirstVisibleItemPosition()..manager.findLastVisibleItemPosition()) {
            manager.scrollToPositionWithOffset(match.line, 0)
        }
    }

    private fun keyboard(): InputMethodManager =
        binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
}
