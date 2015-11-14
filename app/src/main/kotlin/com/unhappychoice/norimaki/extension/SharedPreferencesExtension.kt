package com.unhappychoice.norimaki.extension

import android.content.Context

fun Context.defaultPreference() = this.getSharedPreferences("Default", Context.MODE_PRIVATE)

fun Context.storeStringToPreference(key: String, string: String) {
  val editor = defaultPreferenceEditor()
  editor.putString(key, string)
  editor.apply()
}

private fun Context.defaultPreferenceEditor() = this.getSharedPreferences("Default", Context.MODE_PRIVATE).edit()