package com.unhappychoice.norimaki.presentation.adapter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.model.buildStatus
import com.unhappychoice.norimaki.domain.model.runTime
import com.unhappychoice.norimaki.extension.color
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class BuildStepAdapter internal constructor(
    val context: Context,
    private val diffExecutor: Executor
) : RecyclerView.Adapter<BuildStepAdapter.ViewHolder>() {
    constructor(context: Context) : this(context, sharedDiffExecutor)

    val onClickItem = PublishSubject.create<BuildStepId>()

    @MainThread
    fun submitSteps(newSteps: List<BuildStep>) {
        val snapshot = newSteps.toList()
        val previous = steps
        val generation = ++latestGeneration
        diffExecutor.execute {
            val diff = DiffUtil.calculateDiff(BuildStepDiff(previous, snapshot))
            mainHandler.post {
                if (generation == latestGeneration) {
                    steps = snapshot
                    diff.dispatchUpdatesTo(this)
                }
            }
        }
    }

    override fun getItemCount(): Int = steps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.build_step_list_item_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(steps[position])
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clearBoundItem()
        super.onViewRecycled(holder)
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(step: BuildStep) {
            boundItem = step
            buildTitle.text = step.name
            time.text = step.runTime()
            indicator.setBackgroundColor(step.buildStatus().color())
        }

        fun clearBoundItem() {
            boundItem = null
        }

        private var boundItem: BuildStep? = null

        private val indicator = view.findViewById<View>(R.id.statusIndicator)
        private val buildTitle = view.findViewById<TextView>(R.id.buildTitle)
        private val time = view.findViewById<TextView>(R.id.time)

        init {
            view.setOnClickListener {
                if (boundItem == null) return@setOnClickListener
                val position = bindingAdapterPosition
                val step = steps.getOrNull(position) ?: return@setOnClickListener
                if (step.actions.isNotEmpty()) onClickItem.onNext(BuildStepId.from(step, position))
            }
        }
    }

    private var steps = emptyList<BuildStep>()
    private var latestGeneration = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    private companion object {
        val sharedDiffExecutor: Executor = Executors.newSingleThreadExecutor()
    }
}
