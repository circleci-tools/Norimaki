package com.unhappychoice.norimaki.presentation.view.core

import android.view.Menu
import android.view.MenuItem

interface HasMenu {
    fun onCreateOptionsMenu(menu: Menu?)
    fun onOptionsItemSelected(item: MenuItem?)
}