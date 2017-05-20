package com.unhappychoice.norimaki.infrastructure.pusher.response

import java.util.*

data class ActionOut(
    val type: String,
    val time: Date,
    val message: String
)