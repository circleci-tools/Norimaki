package com.unhappychoice.norimaki.extension

import java.util.*

fun Date.getTimeAgo(): String {
    val before = time / 1000
    val now = Calendar.getInstance().timeInMillis / 1000
    val offset = Calendar.getInstance().timeZone.rawOffset / 1000
    val difference = now - before - offset

    return when {
        difference < 0 -> "now"
        difference < MINUTES -> difference.toString() + "s ago"
        difference < HOURS -> (difference / MINUTES).toString() + "m ago"
        difference < DAYS -> (difference / HOURS).toString() + "h ago"
        difference < WEEKS -> (difference / DAYS).toString() + "D ago"
        difference < MONTHS -> (difference / WEEKS).toString() + "W ago"
        difference < YEARS -> (difference / MONTHS).toString() + "M ago"
        else -> (difference / YEARS).toString() + "Y ago"
    }
}

private val SECONDS = 1
private val MINUTES = 60 * SECONDS
private val HOURS = 60 * MINUTES
private val DAYS = 24 * HOURS
private val WEEKS = 7 * DAYS
private val MONTHS = 4 * WEEKS
private val YEARS = 12 * MONTHS