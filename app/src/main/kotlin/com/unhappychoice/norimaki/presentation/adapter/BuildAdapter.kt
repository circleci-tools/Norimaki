package com.unhappychoice.norimaki.presentation.adapter

import android.content.Context
import android.graphics.Color
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.unhappychoice.circleci.response.Build
import com.jakewharton.rxbinding2.view.clicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.getTimeAgo
import com.unhappychoice.norimaki.extension.subscribeNext
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.PublishSubject

class BuildAdapter(val context: Context) : RecyclerView.Adapter<BuildAdapter.ViewHolder>() {
  val builds = Variable<List<Build>>(emptyList())
  val onClickItem = PublishSubject.create<Build>()

  init {
    setHasStableIds(true)
  }

  override fun getItemId(position: Int) = builds.value[position].hashCode().toLong()

  override fun getItemCount(): Int = builds.value.size

  override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder? {
    val view = LayoutInflater.from(context).inflate(R.layout.build_item_view, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
    holder?.bind(builds.value[position])
  }

  inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
    fun bind(build: Build) {
      repositoryTitle.text = "${build.username} / ${build.reponame}"
      branchTitle.text =  "${build.branch} (${build.vcsRevision?.take(6)})"
      commitTitle.text = build.subject
      createdAt.text = build.queuedAt?.getTimeAgo()

      when (build.status) {
        "success", "fixed", "no_tests" -> indicator.setBackgroundColor(Color.rgb(66, 200, 138))
        "canceled" -> indicator.setBackgroundColor(Color.rgb(137, 137, 137))
        "infrastructure_fail", "timedout", "failed" -> indicator.setBackgroundColor(Color.rgb(237, 92, 92))
        else -> indicator.setBackgroundColor(Color.rgb(92, 211, 228))
      }

      view.clicks()
        .subscribeNext { onClickItem.onNext(build) }
        .addTo(bag)
    }

    private val indicator = view.findViewById(R.id.statusIndicator)
    private val repositoryTitle = view.findViewById(R.id.repositoryTitle) as TextView
    private val branchTitle = view.findViewById(R.id.branchTitle) as TextView
    private val commitTitle = view.findViewById(R.id.commitTitle) as TextView
    private val createdAt = view.findViewById(R.id.createdAt) as TextView
  }

  fun finalize() = bag.dispose()

  private val bag = CompositeDisposable()
}
