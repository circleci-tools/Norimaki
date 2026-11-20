package com.unhappychoice.norimaki

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import com.unhappychoice.norimaki.databinding.ActivityMainBinding
import com.unhappychoice.norimaki.di.activityModule
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.core.GsonParceler
import com.unhappychoice.norimaki.presentation.core.ScreenChanger
import com.unhappychoice.norimaki.presentation.core.goBackFromScreen
import com.unhappychoice.norimaki.presentation.core.subscribeToProjectNavigation
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.view.APITokenView
import com.unhappychoice.norimaki.presentation.view.core.HasMenu
import flow.Flow
import flow.KeyDispatcher
import io.reactivex.disposables.Disposable
import mortar.MortarScope
import mortar.bundler.BundleServiceRunner
import org.kodein.di.DI
import org.kodein.di.instance

class MainActivity : AppCompatActivity() {
    val module by lazy {
        DI {
            extend((application as NorimakiApplication).module)
            import(activityModule(this@MainActivity))
        }
    }

    val drawerToggle by lazy {
        ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, R.string.drawer_open, R.string.drawer_close)
            .apply { setToolbarNavigationClickListener { onBackPressedDispatcher.onBackPressed() } }
    }

    val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var projectNavigation: Disposable? = null
    private var menuOwner: HasMenu? = null
    private var screenMenuItems = emptyList<MenuItem>()
    private val session by lazy {
        val service: SessionService by module.instance()
        service
    }

    private val flowBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!goBackFromCurrentScreen()) finishWithDefaultBack()
        }
    }

    val isTwoPane: Boolean
        get() = resources.getBoolean(R.bool.is_two_pane)

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
        onBackPressedDispatcher.addCallback(this, flowBackCallback)
        setupView()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (projectNavigation != null) return
        val eventBus: EventBusService by module.instance()
        projectNavigation = Flow.get(this).subscribeToProjectNavigation(eventBus, ::isTokenScreenVisible)
    }

    override fun onPause() {
        projectNavigation?.dispose()
        projectNavigation = null
        super.onPause()
    }

    override fun onDestroy() {
        scope.destroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        BundleServiceRunner.getBundleServiceRunner(this).onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val existingItems = menu?.children?.toList().orEmpty()
        menuOwner?.onCreateOptionsMenu(menu)
        screenMenuItems = menu?.children?.filter { item -> existingItems.none { it === item } }?.toList().orEmpty()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            goBackFromCurrentScreen()
            return true
        }
        val owner = menuOwner ?: return super.onOptionsItemSelected(item)
        if (screenMenuItems.none { it === item }) return super.onOptionsItemSelected(item)
        owner.onOptionsItemSelected(item)
        return true
    }

    internal fun setMenuOwner(view: View) {
        menuOwner = view as? HasMenu
        screenMenuItems = emptyList()
        invalidateOptionsMenu()
    }

    private fun setupView() {
        binding.navigationView.di = module
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.drawerLayout.addDrawerListener(drawerToggle)
        lockDrawerOnTwoPane()
        applySystemBarInsets()
    }

    private fun finishWithDefaultBack() {
        flowBackCallback.isEnabled = false
        onBackPressedDispatcher.onBackPressed()
        flowBackCallback.isEnabled = true
    }

    private fun goBackFromCurrentScreen(): Boolean =
        Flow.get(this).goBackFromScreen(session, isTokenScreenVisible())

    private fun isTokenScreenVisible(): Boolean =
        binding.containerView.children.any { it is APITokenView }

    private fun lockDrawerOnTwoPane() {
        if (isTwoPane) binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { view, insets ->
            insets.getInsets(WindowInsetsCompat.Type.systemBars()).let {
                view.setPadding(it.left, it.top, it.right, it.bottom)
            }
            insets
        }
    }

    private fun getFlowContext(baseContext: Context): Context =
        Flow.configure(baseContext, this)
            .dispatcher(KeyDispatcher.configure(this, ScreenChanger(this)).build())
            .defaultKey(BuildListScreen(""))
            .keyParceler(GsonParceler())
            .install()
}
