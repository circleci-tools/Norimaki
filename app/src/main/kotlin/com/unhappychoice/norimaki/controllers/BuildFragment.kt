package com.unhappychoice.norimaki.controllers

import android.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.unhappychoice.norimaki.R

class BuildFragment: Fragment() {
  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    super.onCreateView(inflater, container, savedInstanceState)
    return inflater.inflate(R.layout.fragment_build, container, false)
  }

  override fun onResume() {
    super.onResume()
    setupBinding()
  }

  private fun setupBinding() {

  }
}