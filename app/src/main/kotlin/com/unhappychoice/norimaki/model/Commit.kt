package com.unhappychoice.norimaki.model

import java.util.*

data class Commit(
  val body: String,
  val date: Date,
  val sha1: String,
  val subject: String,
  val urlString: String,
  val author: User,
  val builds: Map<String, Any>,
  val committer: User,
  val project: Project,
  val triggeredBuilds: Map<String, Any>
)