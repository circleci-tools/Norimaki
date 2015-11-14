package com.unhappychoice.norimaki.extension

import android.content.Context
import android.widget.Toast

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
fun Context.longToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()