package com.unhappychoice.norimaki.screen

import android.support.annotation.LayoutRes
import flow.ClassKey

abstract class Screen : ClassKey() {
  @LayoutRes abstract fun getLayoutResource(): Int
}