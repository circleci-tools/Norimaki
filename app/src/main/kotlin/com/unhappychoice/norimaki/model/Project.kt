package com.unhappychoice.norimaki.model

data class Project(
  val parallel: Int,
  val repoName: String,
  val username: String,
  val vcsUrl: String,
  val branches: Map<String, Any>
)
