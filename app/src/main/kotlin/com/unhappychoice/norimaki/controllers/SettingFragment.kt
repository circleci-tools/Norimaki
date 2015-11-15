package com.unhappychoice.norimaki.controllers

import android.os.Bundle
import android.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import butterknife.bindView
import com.jakewharton.rxbinding.view.clicks
import com.jakewharton.rxbinding.widget.textChanges
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.toast
import com.unhappychoice.norimaki.model.AccessToken

class SettingFragment: Fragment() {
  val tokenEdit: EditText by bindView(R.id.token)
  val saveButton: Button by bindView(R.id.save)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.fragment_setting, container, false)
  }

  override fun onResume() {
    super.onResume()
    setupBinding()
  }

  private fun setupBinding() {
    if (isSetupBinding) { return }

    tokenEdit.setText(AccessToken(activity).value())
    tokenEdit.textChanges()
      .doOnNext{ saveButton.setEnabled("$it" != "") }
      .subscribe()

    saveButton.clicks()
      .map { AccessToken(activity).store("${tokenEdit.text}") }
      .doOnNext { activity.toast("Stored token: ${it.value()}") }
      .subscribe()

    isSetupBinding = true
  }

  private var isSetupBinding = false
}