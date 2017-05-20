package com.unhappychoice.norimaki.presentation.screen.core

import android.support.annotation.LayoutRes
import com.unhappychoice.norimaki.ActivityComponent
import flow.ClassKey

abstract class Screen : ClassKey() {
    @LayoutRes abstract fun getLayoutResource(): Int
    abstract fun getSubComponent(activityComponent: ActivityComponent): Any
    abstract fun getTitle(): String
}