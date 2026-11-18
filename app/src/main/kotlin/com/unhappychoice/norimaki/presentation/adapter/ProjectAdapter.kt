package com.unhappychoice.norimaki.presentation.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.Project
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import io.reactivex.subjects.PublishSubject

class ProjectAdapter(val context: Context) : RecyclerView.Adapter<ProjectAdapter.ViewHolder>() {
    val projects = Variable<List<Project>>(emptyList())
    val onClickItem = PublishSubject.create<Project>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = projects.value[position].vcsUrl.hashCode().toLong()

    override fun getItemCount(): Int = projects.value.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.project_list_item_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(projects.value[position])
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clearBoundItem()
        super.onViewRecycled(holder)
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(project: Project) {
            boundItem = project
            projectNameView.text = "${project.username}/${project.reponame}"
        }

        fun clearBoundItem() {
            boundItem = null
        }

        private var boundItem: Project? = null
        private val projectNameView = view.findViewById<TextView>(R.id.projectNameView)

        init {
            view.setOnClickListener { boundItem?.let(onClickItem::onNext) }
        }
    }
}
