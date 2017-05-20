package com.unhappychoice.norimaki.extension

import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView

fun RecyclerView.isNearEnd(): Boolean {
    val layoutManager = layoutManager as LinearLayoutManager
    val total = layoutManager.itemCount
    val lastVisible = layoutManager.findLastVisibleItemPosition()
    return lastVisible >= total - 4
}