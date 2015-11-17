package com.unhappychoice.norimaki.controllers

import android.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import butterknife.bindView
import com.jakewharton.rxbinding.widget.itemClicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.adapter.BuildStepAdapter
import com.unhappychoice.norimaki.api.CircleCIAPIClient
import com.unhappychoice.norimaki.extension.pushFragmentToStack
import com.unhappychoice.norimaki.extension.toast
import com.unhappychoice.norimaki.model.BuildStep
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.ReplaySubject

class BuildFragment(
  val username: String = "",
  val project: String = "",
  val buildNumber: Int = 0): Fragment()
{
  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    super.onCreateView(inflater, container, savedInstanceState)
    return inflater.inflate(R.layout.fragment_build, container, false)
  }

  override fun onResume() {
    super.onResume()
    setupBinding()
  }

  private fun setupBinding() {
    if (isSetupBinding) { return }

    stepList.adapter = adapter
    stepList.itemClicks()
      .subscribe { i ->
        // Show Description
      }

    buildSteps
      .onBackpressureBuffer()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe {
        adapter.buildSteps += it
        adapter.notifyDataSetChanged()
      }

    load()
    isSetupBinding = true
  }

  private fun load() =
    client.getBuild(username, project, buildNumber)
      .map { it.steps.forEach { buildSteps.onNext(it) } }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { res -> activity.toast("Loaded recent builds") },
        { e -> activity.toast("Error occured during loading builds") }
      )

  private var inflater: LayoutInflater? = null

  private var isSetupBinding = false

  private val buildSteps = ReplaySubject<BuildStep>()
  private val stepList: ListView by bindView(R.id.steps)

  private val adapter by lazy { BuildStepAdapter(activity) }
  private val client by lazy { CircleCIAPIClient(activity).instance() }
}