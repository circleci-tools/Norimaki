package com.unhappychoice.norimaki.model

import android.content.Context
import com.unhappychoice.norimaki.extension.defaultPreference
import com.unhappychoice.norimaki.extension.longToast
import com.unhappychoice.norimaki.extension.storeStringToPreference

class AccessToken(val context: Context) {
  fun value(): String {
    val token = context.defaultPreference().getString("token", "")
    if (token == "") {
      context.longToast("Input the CircleCI API token on setting.")
    }
    return token
  }

  fun store(token: String): AccessToken {
    context.storeStringToPreference("token", token)
    return AccessToken(context)
  }
}