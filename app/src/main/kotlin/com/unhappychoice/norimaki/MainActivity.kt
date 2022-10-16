package com.unhappychoice.norimaki

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import com.unhappychoice.norimaki.databinding.ActivityMainBinding
import com.unhappychoice.norimaki.databinding.NavigationViewBinding
import com.unhappychoice.norimaki.di.activityModule
import com.unhappychoice.norimaki.di.applicationModule
import com.unhappychoice.norimaki.presentation.core.GsonParceler
import com.unhappychoice.norimaki.presentation.core.ScreenChanger
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.view.NavigationView
import com.unhappychoice.norimaki.presentation.view.core.HasMenu
import flow.Flow
import flow.KeyDispatcher
import mortar.MortarScope
import mortar.bundler.BundleServiceRunner
import org.kodein.di.DI

class MainActivity : AppCompatActivity() {
    val module by lazy {
        DI {
            import(applicationModule(application as NorimakiApplication))
            import(activityModule(this@MainActivity))
        }
    }

    val drawerToggle by lazy {
        ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, R.string.drawer_open, R.string.drawer_close)
            .apply { setToolbarNavigationClickListener { onBackPressed() } }
    }

    val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val scope: MortarScope by lazy {
        MortarScope.buildChild(applicationContext)
            .withService(BundleServiceRunner.SERVICE_NAME, BundleServiceRunner())
            .build("activity_scope")
    }

    override fun getSystemService(name: String): Any? {
        return when (scope.hasService(name)) {
            true -> scope.getService(name)
            false -> super.getSystemService(name)
        }
    }

    override fun attachBaseContext(baseContext: Context) {
        super.attachBaseContext(getFlowContext(baseContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BundleServiceRunner.getBundleServiceRunner(this).onCreate(savedInstanceState)
        setupView()
    }

    override fun onDestroy() {
        scope.destroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        BundleServiceRunner.getBundleServiceRunner(this).onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        if (!Flow.get(this).goBack()) {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        (getCurrentView() as? HasMenu)?.onCreateOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> Flow.get(this).goBack()
        }
        (getCurrentView() as? HasMenu)?.onOptionsItemSelected(item)
        return true
    }

    private fun setupView() {
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.navigationView.di = module
        binding.drawerLayout.addDrawerListener(drawerToggle)
    }

    private fun getFlowContext(baseContext: Context): Context =
        Flow.configure(baseContext, this)
            .dispatcher(KeyDispatcher.configure(this, ScreenChanger(this)).build())
            .defaultKey(BuildListScreen(""))
            .keyParceler(GsonParceler())
            .install()

    private fun getCurrentView(): View? = binding.containerView.getChildAt(0)
}
