package com.unhappychoice.norimaki.controllers

import android.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import butterknife.bindView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.adapter.BuildAdapter
import com.unhappychoice.norimaki.api.CircleCIAPIClient
import com.unhappychoice.norimaki.model.Build
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.ReplaySubject

class RecentBuildsFragment: Fragment() {
  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.fragment_recent_builds, container, false)
  }

  override fun onResume() {
    super.onResume()
    setupBinding()
  }

  private fun setupBinding() {
    if (isSetupBinding) { return }

    buildList.adapter = adapter

    builds
      .onBackpressureBuffer()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe {
        adapter.builds += it
        adapter.notifyDataSetChanged()
      }

    loadMore()

    isSetupBinding = true
  }

  private fun loadMore() {
    client.getRecentBuildsAcross(offset = page * 10)
      .subscribe {
        it.forEach { builds.onNext(it) }
        page++
      }
  }

  private var isSetupBinding = false
  private var page = 0

  private val builds = ReplaySubject<Build>()
  private val buildList: ListView by bindView(R.id.builds)

  private val adapter by lazy { BuildAdapter(activity) }
  private val client by lazy { CircleCIAPIClient(activity).instance() }
}