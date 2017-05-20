package com.unhappychoice.norimaki.infrastructure.pusher.response

data class Action(
  val step: Int,
  val index: Int,
  val message: String?,
  val log: ActionLog?,
  val out: ActionOut?
)

data class NewAction(
  val step: Int,
  val index: Int,
  val messages: String?,
  val log: ActionLog
)