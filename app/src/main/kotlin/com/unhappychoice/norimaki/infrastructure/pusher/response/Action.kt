package com.unhappychoice.norimaki.infrastructure.pusher.response

data class Action(
    val step: Int,
    val index: Int,
    val messages: String?,
    val log: ActionLog
)

data class OutAction(
    val step: Int,
    val index: Int,
    val messages: String?,
    val out: ActionOut
)