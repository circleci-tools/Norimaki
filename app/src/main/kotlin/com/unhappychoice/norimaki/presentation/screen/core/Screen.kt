package com.unhappychoice.norimaki.presentation.screen.core

import android.support.annotation.LayoutRes
import com.github.salomonbrys.kodein.Kodein
import flow.ClassKey

abstract class Screen : ClassKey() {
    @LayoutRes abstract fun getLayoutResource(): Int
    abstract fun getTitle(): String
    open fun module(activityModule: Kodein): Kodein = Kodein { extend(activityModule) }
}