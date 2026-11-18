package com.unhappychoice.norimaki.presentation.adapter

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.log.BuildLog
import com.unhappychoice.norimaki.presentation.log.LogLineCursor
import com.unhappychoice.norimaki.presentation.log.LogSpan
import com.unhappychoice.norimaki.presentation.log.LogUpdate

internal class BuildLogAdapter(private val log: BuildLog) : RecyclerView.Adapter<BuildLogAdapter.ViewHolder>() {
    override fun getItemCount(): Int = log.lineCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.build_log_line, parent, false) as TextView)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.cursor = LogLineCursor()
        val text = styledText(holder.cursor.takeNewSpans(log[position]))
        holder.textView.setText(text, TextView.BufferType.EDITABLE)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty() || payloads.any { it !== AppendLog }) {
            onBindViewHolder(holder, position)
            return
        }

        val spans = holder.cursor.takeNewSpans(log[position])
        if (spans.isNotEmpty()) holder.textView.append(styledText(spans))
    }

    fun update(change: LogUpdate) {
        change.changedLine?.let { notifyItemChanged(it, AppendLog) }
        if (change.insertCount > 0) notifyItemRangeInserted(change.insertStart, change.insertCount)
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
    }

    private object AppendLog
}
