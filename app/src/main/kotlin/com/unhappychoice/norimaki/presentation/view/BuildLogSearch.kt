package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.graphics.Rect
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.doAfterTextChanged
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.BuildStepViewBinding
import com.unhappychoice.norimaki.extension.subscribeNext
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.SerialDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.ceil

internal class BuildLogSearch(private val binding: BuildStepViewBinding) {
    private val search = SerialDisposable()
    private val highlight = BackgroundColorSpan(0x805285C9.toInt())
    private var matches = emptyList<IntRange>()
    private var selected = 0

    fun bind(bag: CompositeDisposable) = with(binding) {
        bag.add(search)
        logFind.setOnClickListener { open() }
        logCloseSearch.setOnClickListener {
            close()
            logFind.requestFocus()
        }
        logQuery.doAfterTextChanged { refresh() }
        logPrevious.setOnClickListener { move(-1) }
        logNext.setOnClickListener { move(1) }
    }

    fun setAvailable(available: Boolean) {
        if (!available) close()
        binding.logFind.visibility =
            if (available && binding.logSearchPanel.visibility != View.VISIBLE) View.VISIBLE else View.GONE
    }

    fun render(text: CharSequence) {
        binding.logText.text = text
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
        matches = emptyList()
        updateMatch(null)
        val query = logQuery.text.toString()
        if (query.isEmpty() || logSearchPanel.visibility != View.VISIBLE) return@with

        logMatchCount.setText(R.string.build_log_searching)
        val snapshot = logText.text
        search.set(
            Observable.timer(150, TimeUnit.MILLISECONDS, Schedulers.computation())
                .flatMap { findLogMatches(snapshot, query) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeNext {
                    matches = it
                    selected = 0
                    updateMatch(it.firstOrNull())
                    if (it.isEmpty()) logMatchCount.setText(R.string.build_log_no_matches)
                }
        )
    }

    private fun move(delta: Int) {
        if (matches.isEmpty()) return
        selected = (selected + delta + matches.size) % matches.size
        updateMatch(matches[selected])
    }

    private fun updateMatch(match: IntRange?) = with(binding) {
        logPrevious.isEnabled = matches.size > 1
        logNext.isEnabled = matches.size > 1
        val text = logText.text as? Spannable
        text?.removeSpan(highlight)
        if (match == null) {
            logMatchCount.text = ""
            return@with
        }

        logMatchCount.text = root.context.getString(R.string.build_log_match_count, selected + 1, matches.size)
        text?.setSpan(highlight, match.first, match.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        logText.post {
            if (text?.getSpanStart(highlight) == match.first && logText.text === text) scrollTo(match.first)
        }
    }

    private fun scrollTo(offset: Int) = with(binding.logText) {
        val layout = layout ?: return@with
        val line = layout.getLineForOffset(offset)
        val x = ceil(layout.getPrimaryHorizontal(offset)).toInt() + totalPaddingLeft
        val top = layout.getLineTop(line) + totalPaddingTop
        requestRectangleOnScreen(Rect(x - 1, top, x + 1, layout.getLineBottom(line) + totalPaddingTop), true)
    }

    private fun keyboard(): InputMethodManager =
        binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
}

private fun findLogMatches(text: CharSequence, query: String): Observable<List<IntRange>> =
    Observable.create { emitter ->
        val matches = mutableListOf<IntRange>()
        if (query.isNotEmpty()) {
            val matcher = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                .matcher(text.toString())
            while (!emitter.isDisposed && matcher.find()) {
                matches.add(matcher.start() until matcher.end())
            }
        }
        if (!emitter.isDisposed) {
            emitter.onNext(matches)
            emitter.onComplete()
        }
    }
