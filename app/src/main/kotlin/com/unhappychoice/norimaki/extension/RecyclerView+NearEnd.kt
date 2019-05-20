package com.unhappychoice.norimaki.extension

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.isNearEnd(): Boolean {
    val layoutManager = layoutManager as LinearLayoutManager
    val total = layoutManager.itemCount
    val lastVisible = layoutManager.findLastVisibleItemPosition()
    return lastVisible >= total - 4
}