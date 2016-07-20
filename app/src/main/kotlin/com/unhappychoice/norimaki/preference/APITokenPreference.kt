package com.unhappychoice.norimaki.preference

import android.content.Context
import jp.takuji31.koreference.KoreferenceModel
import jp.takuji31.koreference.stringPreference

class APITokenPreference(context: Context) : KoreferenceModel(context, name = "access_token") {
  var token: String by stringPreference("")
}