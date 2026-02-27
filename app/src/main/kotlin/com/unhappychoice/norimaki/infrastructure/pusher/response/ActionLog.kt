package com.unhappychoice.norimaki.infrastructure.pusher.response

import java.util.*

// Kept as stub - Pusher integration is no longer supported with CircleCI API V2
data class ActionLog(
    val background: Boolean,
    val endTime: Date,
    val hasOutput: Boolean,
    val index: Int,
    val name: String,
    val outputUrl: String?,
    val parallel: Boolean,
    val runTimeMillis: Int?,
    val status: String,
    val startTime: Date,
    val step: Int,
    val type: String
)
