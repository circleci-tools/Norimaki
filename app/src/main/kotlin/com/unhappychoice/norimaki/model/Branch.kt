package com.unhappychoice.norimaki.model

data class Branch(
  val name: String,
  val builds: Map<String, Any>,
  val branchID: String,
  val project: Project,
  val pushers: Map<String, Any>
)
