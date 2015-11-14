package com.unhappychoice.norimaki.model

import java.util.*

data class BuildAction(
  val truncated: Boolean,
  val index: Int,
  val parallel: Boolean,
  val failed: Boolean,
  val infrastructureFail: Boolean,
  val name: String,
  val bashCommand: String,
  val status: String,
  val timedout: Boolean,
  val endTime: Date,
  val type: String,
  val messages: List<String>,
  val outputUrl: String,
  val startTime: Date,
  val exitCode: Int,
  val canceled: Boolean,
  val step: Int,
  val runTimeMillis: Int,
  val hasOutput: Boolean
)