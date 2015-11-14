package com.unhappychoice.norimaki.model

data class User(
  val selectedEmail: String,
  val login: String,
  val name: String,
  val avatarUrl: String,
  val projects: Map<String, Map<String, Any>>
)
