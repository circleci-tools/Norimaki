package com.unhappychoice.norimaki.presentation.adapter

import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.RecyclerView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.log.BuildLog
import com.unhappychoice.norimaki.presentation.log.LogLineCursor
import com.unhappychoice.norimaki.presentation.log.LogMatch
import com.unhappychoice.norimaki.presentation.log.LogSpan
import com.unhappychoice.norimaki.presentation.log.LogUpdate

internal class BuildLogAdapter(private val log: BuildLog) : RecyclerView.Adapter<BuildLogAdapter.ViewHolder>() {
    private var match: LogMatch? = null
    private var pendingReveal: LogMatch? = null

    override fun getItemCount(): Int = log.lineCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.build_log_line, parent, false) as TextView)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.cursor = LogLineCursor()
        val text = styledText(holder.cursor.takeNewSpans(log[position]))
        holder.textView.setText(text, TextView.BufferType.EDITABLE)
        bindHighlight(holder, position)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty() || payloads.any { it !== AppendLog && it !== Highlight }) {
            onBindViewHolder(holder, position)
            return
        }

        val spans = holder.cursor.takeNewSpans(log[position])
        if (spans.isNotEmpty()) holder.textView.append(styledText(spans))
        if (Highlight in payloads) bindHighlight(holder, position)
    }

    fun update(change: LogUpdate) {
        if (change.removedCount > 0) notifyItemRangeRemoved(0, change.removedCount)
        change.changedLine?.let { notifyItemChanged(it, AppendLog) }
        if (change.insertCount > 0) notifyItemRangeInserted(change.insertStart, change.insertCount)
    }

    fun highlight(next: LogMatch?) {
        val previousLine = match?.line
        match = next
        pendingReveal = next
        listOfNotNull(previousLine, next?.line).distinct().forEach { line ->
            if (line < itemCount) notifyItemChanged(line, Highlight)
        }
    }

    private fun bindHighlight(holder: ViewHolder, position: Int) = with(holder.textView) {
        val text = editableText
        text.removeSpan(holder.highlight)
        val selected = match?.takeIf { it.line == position } ?: return@with
        text.setSpan(holder.highlight, selected.start, selected.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (pendingReveal != selected) return@with
        doOnLayout {
            post {
                if (pendingReveal == selected && holder.bindingAdapterPosition == selected.line && editableText === text) {
                    pendingReveal = null
                    revealMatch(selected)
                }
            }
        }
    }

    private fun TextView.revealMatch(selected: LogMatch) {
        val textLayout = layout ?: return
        val selection = Path()
        textLayout.getSelectionPath(selected.start, selected.end, selection)
        val bounds = RectF()
        selection.computeBounds(bounds, true)
        bounds.offset(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
        val rectangle = Rect()
        bounds.roundOut(rectangle)
        requestRectangleOnScreen(rectangle, true)
    }

    private fun styledText(spans: List<LogSpan>): SpannableStringBuilder {
        val text = SpannableStringBuilder()
        spans.forEach { span ->
            val start = text.length
            text.append(span.text)
            span.color?.let { color ->
                text.setSpan(ForegroundColorSpan(color), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return text
    }

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView) {
        var cursor = LogLineCursor()
        val highlight = BackgroundColorSpan(0x805285C9.toInt())
    }

    private object Highlight

    private object AppendLog
}
