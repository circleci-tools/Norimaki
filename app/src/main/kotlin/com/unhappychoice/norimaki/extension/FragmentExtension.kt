package com.unhappychoice.norimaki.extension

import android.app.Activity
import android.app.Fragment

fun Activity.pushFragmentToStack(layout: Int, fragment: Fragment) {
  val t = fragmentManager.beginTransaction()
  t.add(layout, fragment)
  t.addToBackStack(null)
  t.commit()
}