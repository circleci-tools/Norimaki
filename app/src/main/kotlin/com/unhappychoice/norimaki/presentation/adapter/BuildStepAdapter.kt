package com.unhappychoice.norimaki.presentation.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.jakewharton.rxbinding2.view.clicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.runTime
import com.unhappychoice.norimaki.domain.model.statusColor
import com.unhappychoice.norimaki.extension.subscribeNext
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.PublishSubject

class BuildStepAdapter(val context: Context) : RecyclerView.Adapter<BuildStepAdapter.ViewHolder>() {
    val onClickItem = PublishSubject.create<BuildStep>()

    fun submitSteps(newSteps: List<BuildStep>) {
        val snapshot = newSteps.toList()
        val diff = DiffUtil.calculateDiff(BuildStepDiff(steps, snapshot))
        steps = snapshot
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = steps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.build_step_list_item_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(steps[position])
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

        private val indicator = view.findViewById<View>(R.id.statusIndicator)
        private val buildTitle = view.findViewById<TextView>(R.id.buildTitle)
        private val time = view.findViewById<TextView>(R.id.time)
    }

    fun finalize() = bag.dispose()

    private var steps = emptyList<BuildStep>()
    private val bag = CompositeDisposable()
}
