package com.unhappychoice.norimaki.infrastructure.preference

import android.content.Context
import jp.takuji31.koreference.KoreferenceModel
import jp.takuji31.koreference.longPreference

class NotificationPreference(context: Context) : KoreferenceModel(context, name = "notification") {
    var lastStopTime: Long by longPreference(0L)
}
