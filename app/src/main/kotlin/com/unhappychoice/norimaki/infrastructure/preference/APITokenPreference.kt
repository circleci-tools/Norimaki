package com.unhappychoice.norimaki.infrastructure.preference

import android.content.Context
import jp.takuji31.koreference.KoreferenceModel
import jp.takuji31.koreference.stringPreference

class APITokenPreference(context: Context) : KoreferenceModel(context, name = API_TOKEN_PREFERENCE_NAME) {
    var token: String by stringPreference("")
}

internal const val API_TOKEN_PREFERENCE_NAME = "access_token"
