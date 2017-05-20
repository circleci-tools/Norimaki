package com.unhappychoice.norimaki.presentation.adapter

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.unhappychoice.circleci.response.BuildStep
import com.jakewharton.rxbinding2.view.clicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.runTime
import com.unhappychoice.norimaki.domain.model.statusColor
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.subscribeNext
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.PublishSubject

class BuildStepAdapter(val context: Context) : RecyclerView.Adapter<BuildStepAdapter.ViewHolder>() {
    val steps = Variable<List<BuildStep>>(emptyList())
    val onClickItem = PublishSubject.create<BuildStep>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = steps.value[position].hashCode().toLong()

    override fun getItemCount(): Int = steps.value.size

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder? {
        val view = LayoutInflater.from(context).inflate(R.layout.build_step_list_item_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        holder?.bind(steps.value[position])
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(step: BuildStep) {
            buildTitle.text = step.name
            time.text = step.runTime()
            indicator.setBackgroundColor(step.statusColor())

            view.clicks()
                .subscribeNext { onClickItem.onNext(step) }
                .addTo(bag)
        }

        private val indicator = view.findViewById(R.id.statusIndicator)
        private val buildTitle = view.findViewById(R.id.buildTitle) as TextView
        private val time = view.findViewById(R.id.time) as TextView
    }

    fun finalize() = bag.dispose()

    private val bag = CompositeDisposable()
}
