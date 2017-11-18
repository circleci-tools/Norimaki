package com.unhappychoice.norimaki.presentation.adapter

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bumptech.glide.Glide
import com.github.unhappychoice.circleci.response.Build
import com.jakewharton.rxbinding2.view.clicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.*
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.getTimeAgo
import com.unhappychoice.norimaki.extension.subscribeNext
import de.hdodenhof.circleimageview.CircleImageView
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.PublishSubject

class BuildAdapter(val context: Context) : RecyclerView.Adapter<BuildAdapter.ViewHolder>() {
    val builds = Variable<List<Build>>(emptyList())
    val onClickItem = PublishSubject.create<Build>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = builds.value[position].uniqueId().hashCode().toLong()

    override fun getItemCount(): Int = builds.value.size

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder? {
        val view = LayoutInflater.from(context).inflate(R.layout.build_list_item_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        holder?.bind(builds.value[position])
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(build: Build) {
            repositoryTitle.text = build.repositoryString()
            branchTitle.text = build.revisionString()
            commitTitle.text = build.subject
            createdAt.text = build.queuedAt?.getTimeAgo()
            indicator.setBackgroundColor(build.statusColor())

            Glide.with(context).load(build.avatarUrl())
                .into(author)

            view.clicks()
                .subscribeNext { onClickItem.onNext(build) }
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
