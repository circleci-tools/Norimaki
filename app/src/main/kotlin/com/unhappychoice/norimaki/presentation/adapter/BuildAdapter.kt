package com.unhappychoice.norimaki.presentation.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.*
import com.unhappychoice.norimaki.extension.color
import com.unhappychoice.norimaki.extension.getTimeAgo
import de.hdodenhof.circleimageview.CircleImageView
import io.reactivex.subjects.PublishSubject

class BuildAdapter(val context: Context) : ListAdapter<Build, BuildAdapter.ViewHolder>(BuildDiff) {
    val onClickItem = PublishSubject.create<Build>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.build_list_item_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.refreshElapsedTime()
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clearBoundItem()
        super.onViewRecycled(holder)
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(build: Build) {
            boundItem = build
            repositoryTitle.text = build.repositoryWithWorkflowString()
            branchTitle.text = build.revisionString()
            commitTitle.text = build.subject
            refreshElapsedTime()
            indicator.setBackgroundColor(build.buildStatus().color())

            Glide.with(context).load(build.avatarUrl())
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .into(author)
        }

        internal fun refreshElapsedTime() {
            val label = boundItem?.queuedAt?.getTimeAgo()
            if (createdAt.text.toString() != label.orEmpty()) createdAt.text = label
        }

        fun clearBoundItem() {
            boundItem = null
        }

        private var boundItem: Build? = null

        private val author = view.findViewById<CircleImageView>(R.id.author)
        private val indicator = view.findViewById<View>(R.id.statusIndicator)
        private val repositoryTitle = view.findViewById<TextView>(R.id.repositoryTitle)
        private val branchTitle = view.findViewById<TextView>(R.id.branchTitle)
        private val commitTitle = view.findViewById<TextView>(R.id.commitTitle)
        private val createdAt = view.findViewById<TextView>(R.id.createdAt)

        init {
            view.setOnClickListener { boundItem?.let(onClickItem::onNext) }
        }
    }
}

internal object BuildDiff : DiffUtil.ItemCallback<Build>() {
    override fun areItemsTheSame(oldItem: Build, newItem: Build) =
        oldItem.uniqueId() == newItem.uniqueId()

    override fun areContentsTheSame(oldItem: Build, newItem: Build) = oldItem == newItem
}
