package com.unhappychoice.norimaki.infrastructure.pusher.response

import java.util.*

data class ActionLog(
  val step: Int,
  val name: String,
  val index: Int,
  val parallel: Boolean,
  val status: String,
  val type: String,
  val startTime: Date,
  val background: Boolean,
  val hasOutput: Boolean
)