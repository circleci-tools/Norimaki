package com.unhappychoice.norimaki.model

data class BuildStep(
  val name: String,
  val actions: List<BuildAction>
)
