package com.unhappychoice.norimaki.extension

import android.graphics.Color
import com.unhappychoice.norimaki.domain.model.BuildStatus

fun BuildStatus.color(): Int = when (this) {
    BuildStatus.SUCCESS -> Color.rgb(66, 200, 138)
    BuildStatus.CANCELED -> Color.rgb(137, 137, 137)
    BuildStatus.FAILED -> Color.rgb(237, 92, 92)
    BuildStatus.ACTIVE -> Color.rgb(92, 211, 228)
}
