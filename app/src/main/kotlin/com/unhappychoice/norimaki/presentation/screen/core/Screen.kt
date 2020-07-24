package com.unhappychoice.norimaki.presentation.screen.core

import androidx.annotation.LayoutRes
import flow.ClassKey
import org.kodein.di.DI

abstract class Screen : ClassKey() {
    @LayoutRes
    abstract fun getLayoutResource(): Int
    abstract fun getTitle(): String
    open fun module(activityModule: DI): DI = DI { extend(activityModule) }
}