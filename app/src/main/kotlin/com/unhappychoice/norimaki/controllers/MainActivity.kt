package com.unhappychoice.norimaki.controllers

import android.app.AlertDialog
import android.support.v7.app.ActionBarActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.pushFragmentToStack

public class MainActivity : ActionBarActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    pushFragmentToStack(R.id.container, SettingFragment())
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    getMenuInflater().inflate(R.menu.menu_main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem?): Boolean {
    val id = item!!.getItemId()

    if (id == R.id.action_settings) {
      pushFragmentToStack(R.id.container, SettingFragment())
      return true
    }

    return super.onOptionsItemSelected(item)
  }

  override fun onBackPressed() {
    if (hasFragments()) fragmentManager.popBackStack() else showFinishConfirmation()
  }

  private fun hasFragments(): Boolean {
    return fragmentManager.getBackStackEntryCount() != 0
  }

  private fun showFinishConfirmation() {
    AlertDialog.Builder(this)
      .setTitle("Finish Norimaki")
      .setMessage("Do you want to finish norimaki ?")
      .setPositiveButton("OK") { _1, _2 -> finish() }
      .setNegativeButton("Cancel", null)
      .show()
  }
}

