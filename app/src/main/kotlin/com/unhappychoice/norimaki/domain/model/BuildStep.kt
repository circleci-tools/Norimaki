package com.unhappychoice.norimaki.domain.model

import android.graphics.Color
import com.github.unhappychoice.circleci.v2.response.Job

fun Job.statusColor(): Int = when (status) {
    "success" -> Color.rgb(66, 200, 138)
    "canceled" -> Color.rgb(137, 137, 137)
    "infrastructure_fail", "timedout", "failed" -> Color.rgb(237, 92, 92)
    else -> Color.rgb(92, 211, 228)
}
