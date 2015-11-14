package com.unhappychoice.norimaki.controllers

import android.os.Bundle
import android.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.jakewharton.rxbinding.view.clicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.toast
import com.unhappychoice.norimaki.model.AccessToken

class SettingFragment: Fragment() {
  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    val view = inflater.inflate(R.layout.fragment_setting, container, false)

    val token = view.findViewById(R.id.token) as TextView
    val button = view.findViewById(R.id.save) as Button

    button.clicks()
      .map { AccessToken(activity).store("${token.text}") }
      .doOnNext { activity.toast("Stored token: ${it.value()}") }
      .subscribe()

    return view
  }
}