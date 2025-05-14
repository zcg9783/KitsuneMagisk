package com.topjohnwu.magisk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.forEach
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.NavDirections
import com.topjohnwu.magisk.MainDirections
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.arch.startAnimations
import com.topjohnwu.magisk.arch.viewModel
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.isRunningAsStub
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.databinding.ActivityMainMd2Binding
import com.topjohnwu.magisk.ui.home.HomeFragmentDirections
import com.topjohnwu.magisk.view.MagiskDialog
import com.topjohnwu.magisk.view.Shortcuts
import java.io.File

class MainViewModel : BaseViewModel()

class MainActivity : SplashActivity<ActivityMainMd2Binding>() {

    override val layoutRes = R.layout.activity_main_md2
    override val viewModel by viewModel<MainViewModel>()
    override val navHostId: Int = R.id.main_nav_host
    override val snackbarView: View
        get() {
            val fragmentOverride = currentFragment?.snackbarView
            return fragmentOverride ?: super.snackbarView
        }
    override val snackbarAnchorView: View?
        get() {
            val fragmentAnchor = currentFragment?.snackbarAnchorView
            return when {
                fragmentAnchor?.isVisible == true -> fragmentAnchor
                binding.mainNavigation.isVisible -> return binding.mainNavigation
                else -> null
            }
        }

    private var isRootFragment = true

    @SuppressLint("InlinedApi")
    override fun showMainUI(savedInstanceState: Bundle?) {
        setContentView()
        showUnsupportedMessage()
        askForHomeShortcut()

        // Ask permission to post notifications for background update check
        if (Config.checkUpdate) {
            withPermission(Manifest.permission.POST_NOTIFICATIONS) {
                Config.checkUpdate = it
            }
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        navigation.addOnDestinationChangedListener { _, destination, _ ->
            isRootFragment = when (destination.id) {
                R.id.homeFragment,
                R.id.modulesFragment,
                R.id.superuserFragment,
                R.id.logFragment -> true
                else -> false
            }

            setDisplayHomeAsUpEnabled(!isRootFragment)
            requestNavigationHidden(!isRootFragment)

            binding.mainNavigation.menu.forEach {
                if (it.itemId == destination.id) {
                    it.isChecked = true
                }
            }
        }

        setSupportActionBar(binding.mainToolbar)

        binding.mainNavigation.setOnItemSelectedListener {
            getScreen(it.itemId)?.navigate()
            true
        }
        binding.mainNavigation.setOnItemReselectedListener {
            // https://issuetracker.google.com/issues/124538620
        }
        binding.mainNavigation.menu.apply {
            findItem(R.id.superuserFragment)?.isEnabled = Info.showSuperUser
            findItem(R.id.modulesFragment)?.isEnabled = Info.env.isActive && LocalModule.loaded()
        }

        val section =
            if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES)
                Const.Nav.SETTINGS
            else
                intent.getStringExtra(Const.Key.OPEN_SECTION)

        getScreen(section)?.navigate()

        if (!isRootFragment) {
            requestNavigationHidden(requiresAnimation = savedInstanceState == null)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    fun setDisplayHomeAsUpEnabled(isEnabled: Boolean) {
        binding.mainToolbar.startAnimations()
        when {
            isEnabled -> binding.mainToolbar.setNavigationIcon(R.drawable.ic_back_md2)
            else -> binding.mainToolbar.navigationIcon = null
        }
    }

    internal fun requestNavigationHidden(hide: Boolean = true, requiresAnimation: Boolean = true) {
        val bottomView = binding.mainNavigation
        if (requiresAnimation) {
            bottomView.isVisible = true
            bottomView.isHidden = hide
        } else {
            bottomView.isGone = hide
        }
    }

    fun invalidateToolbar() {
        //binding.mainToolbar.startAnimations()
        bind