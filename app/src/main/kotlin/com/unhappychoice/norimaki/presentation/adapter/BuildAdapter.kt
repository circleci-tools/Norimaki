package com.unhappychoice.norimaki.presentation.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v2.response.Workflow
import com.jakewharton.rxbinding2.view.clicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.projectName
import com.unhappychoice.norimaki.domain.model.statusColor
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.subscribeNext
import de.hdodenhof.circleimageview.CircleImageView
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.PublishSubject

class BuildAdapter(val context: Context) : RecyclerView.Adapter<BuildAdapter.ViewHolder>() {
    val builds = Variable<List<Workflow>>(emptyList())
    val onClickItem = PublishSubject.create<Workflow>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = builds.value[position].id.hashCode().toLong()

    override fun getItemCount(): Int = builds.value.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.build_list_item_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(builds.value[position])
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(workflow: Workflow) {
            repositoryTitle.text = workflow.projectName()
            branchTitle.text = "${workflow.name} #${workflow.pipelineNumber ?: ""}"
            commitTitle.text = workflow.status
            createdAt.text = workflow.createdAt.take(10)
            indicator.setBackgroundColor(workflow.statusColor())

            view.clicks()
                .subscribeNext { onClickItem.onNext(workflow) }
                .addTo(bag)
        }

        private val author = view.findViewById<CircleImageView>(R.id.author)
        private val indicator = view.findViewById<View>(R.id.statusIndicator)
        private val repositoryTitle = view.findViewById<TextView>(R.id.repositoryTitle)
        private val branchTitle = view.findViewById<TextView>(R.id.branchTitle)
        private val commitTitle = view.findViewById<TextView>(R.id.commitTitle)
        private val createdAt = view.findViewById<TextView>(R.id.createdAt)
    }

    fun finalize() = bag.dispose()

    private val bag = CompositeDisposable()
}
