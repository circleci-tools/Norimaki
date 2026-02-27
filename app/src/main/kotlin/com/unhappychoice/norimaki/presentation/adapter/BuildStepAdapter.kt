package com.unhappychoice.norimaki.presentation.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v2.response.Job
import com.jakewharton.rxbinding2.view.clicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.statusColor
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.subscribeNext
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.PublishSubject

class BuildStepAdapter(val context: Context) : RecyclerView.Adapter<BuildStepAdapter.ViewHolder>() {
    val steps = Variable<List<Job>>(emptyList())
    val onClickItem = PublishSubject.create<Job>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = steps.value[position].id.hashCode().toLong()

    override fun getItemCount(): Int = steps.value.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.build_step_list_item_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(steps.value[position])
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(job: Job) {
            buildTitle.text = job.name
            time.text = job.status
            indicator.setBackgroundColor(job.statusColor())

            view.clicks()
                .subscribeNext { onClickItem.onNext(job) }
                .addTo(bag)
        }

        private val indicator = view.findViewById<View>(R.id.statusIndicator)
        private val buildTitle = view.findViewById<TextView>(R.id.buildTitle)
        private val time = view.findViewById<TextView>(R.id.time)
    }

    fun finalize() = bag.dispose()

    private val bag = CompositeDisposable()
}
