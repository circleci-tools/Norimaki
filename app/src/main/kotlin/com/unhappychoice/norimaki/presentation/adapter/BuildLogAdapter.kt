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
import com.unhappychoice.norimaki.presentation.log.LogUpdate

internal class BuildLogAdapter(private val log: BuildLog) : RecyclerView.Adapter<BuildLogAdapter.ViewHolder>() {
    override fun getItemCount(): Int = log.lineCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.build_log_line, parent, false) as TextView)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val text = SpannableStringBuilder()
        log[position].forEach { span ->
            val start = text.length
            text.append(span.text)
            span.color?.let { color ->
                text.setSpan(ForegroundColorSpan(color), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        holder.textView.text = text
    }

    fun update(change: LogUpdate) {
        change.changedLine?.let { notifyItemChanged(it) }
        if (change.insertCount > 0) notifyItemRangeInserted(change.insertStart, change.insertCount)
    }

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}
