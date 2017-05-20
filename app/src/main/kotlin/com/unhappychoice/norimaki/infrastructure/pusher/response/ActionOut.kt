package com.unhappychoice.norimaki.infrastructure.pusher.response

import java.util.Date

data class ActionOut(
  val type: String,
  val time: Date,
  val message: String
)