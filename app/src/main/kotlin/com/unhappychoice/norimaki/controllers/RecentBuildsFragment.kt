package com.unhappychoice.norimaki.controllers

import android.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import butterknife.bindView
import com.jakewharton.rxbinding.view.clicks
import com.jakewharton.rxbinding.widget.itemClicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.adapter.BuildAdapter
import com.unhappychoice.norimaki.api.CircleCIAPIClient
import com.unhappychoice.norimaki.extension.pushFragmentToStack
import com.unhappychoice.norimaki.extension.toast
import com.unhappychoice.norimaki.model.Build
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.ReplaySubject

class RecentBuildsFragment: Fragment() {
  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    super.onCreateView(inflater, container, savedInstanceState)
    this.inflater = inflater
    return inflater.inflate(R.layout.fragment_recent_builds, container, false)
  }

  override fun onResume() {
    super.onResume()
    setupBinding()
  }

  private fun setupBinding() {
    if (isSetupBinding) { return }

    val footerView = inflater!!.inflate(R.layout.load_more_button, null, false)
    val loadButton = footerView.findViewById(R.id.loadButton)

    buildList.addFooterView(footerView)
    buildList.adapter = adapter
    buildList.itemClicks()
      .subscribe { i ->
        val build = adapter.builds[i]
        val f = BuildFragment(build.username, build.reponame, build.buildNum)
        activity.pushFragmentToStack(R.id.container, f)
      }

    loadButton.clicks()
      .doOnNext { loadButton.setEnabled(false) }
      .subscribe { loadMore() }

    builds
      .onBackpressureBuffer()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnNext { loadButton.setEnabled(true) }
      .doOnError { loadButton.setEnabled(true) }
      .subscribe {
        adapter.builds += it
        adapter.notifyDataSetChanged()
      }

    loadMore()
    isSetupBinding = true
  }

  private fun loadMore() =
    client.getRecentBuildsAcross(limit = 20, offset = page * 20)
      .map { it.forEach { builds.onNext(it) } }
      .observeOn(AndroidSchedulers.mainThread())
      .doOnNext { page++ }
      .subscribe(
        { res -> activity.toast("Loaded recent builds") },
        { e -> activity.toast("Error occured during loading builds") }
      )

  private var inflater: LayoutInflater? = null

  private var isSetupBinding = false
  private var page = 0

  private val builds = ReplaySubject<Build>()
  private val buildList: ListView by bindView(R.id.builds)

  private val adapter by lazy { BuildAdapter(activity) }
  private val client by lazy { CircleCIAPIClient(activity).instance() }
}