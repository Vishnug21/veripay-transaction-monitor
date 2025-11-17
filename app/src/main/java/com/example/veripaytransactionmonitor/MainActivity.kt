package com.example.veripaytransactionmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.veripaytransactionmonitor.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivityNav"
    private lateinit var binding: ActivityMainBinding

    // Permissions we need for SMS auto-verify
    private val SMS_PERMS = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    )

    // ActivityResult launcher for permissions
    private val requestSmsPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val granted = perms.entries.all { it.value == true }
            if (granted) {
                Snackbar.make(binding.root, "SMS permission granted", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, "SMS permission denied — auto-verification won't work", Snackbar.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use viewBinding if enabled, otherwise setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Find NavHostFragment and NavController
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val navController: NavController = navHostFragment?.navController
            ?: throw IllegalStateException("NavHostFragment not found. Make sure activity_main.xml uses NavHostFragment with id nav_host_fragment")

        // BottomNavigationView
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Preferred wiring: let Navigation component manage menu <-> destinations
        try {
            bottomNav.setupWithNavController(navController)
        } catch (e: Exception) {
            Log.w(TAG, "setupWithNavController failed: ${e.message}")
        }

        // Fallback explicit handling (keeps previous UX if you need custom behavior)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment -> {
                    val startId = navController.graph.startDestinationId
                    navController.popBackStack(startId, false)
                    navController.navigate(R.id.homeFragment)
                    true
                }
                R.id.dashboardFragment -> {
                    navController.navigate(R.id.dashboardFragment)
                    true
                }
                R.id.paymentFragment -> {
                    navController.navigate(R.id.paymentFragment)
                    true
                }
                R.id.settingsFragment -> {
                    navController.navigate(R.id.settingsFragment)
                    true
                }
                else -> false
            }
        }

        // handle reselection (tap same tab again) - pop to root
        bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.homeFragment) {
                val startId = navController.graph.startDestinationId
                navController.popBackStack(startId, false)
            }
        }

        // NOTE: do NOT auto-request permissions on start unless you want immediate prompt.
        // You can call checkAndPromptSmsPermission() from SettingsFragment button or here if preferred.
    }

    /**
     * Check and request SMS permissions (RECEIVE_SMS, READ_SMS)
     * Shows rationale dialog if necessary.
     */
    fun checkAndPromptSmsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val missing = SMS_PERMS.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) {
                Snackbar.make(binding.root, "SMS permissions already granted", Snackbar.LENGTH_SHORT).show()
                return
            }

            val shouldShowRationale = missing.any { shouldShowRequestPermissionRationale(it) }
            if (shouldShowRationale) {
                AlertDialog.Builder(this)
                    .setTitle("SMS permission required")
                    .setMessage("To automatically verify payments we need access to incoming SMS. This is used only for automatic verification and not to read or store other messages.")
                    .setPositiveButton("Allow") { _, _ ->
                        requestSmsPermissionsLauncher.launch(SMS_PERMS)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                // direct request
                requestSmsPermissionsLauncher.launch(SMS_PERMS)
            }
        } else {
            Snackbar.make(binding.root, "SMS permission not required on this Android version", Snackbar.LENGTH_SHORT).show()
        }
    }

    /**
     * Open Notification Access settings so user can enable NotificationListener for this app.
     * The user must manually enable the listener. This Intent opens the system settings page.
     */
    fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        try {
            startActivity(intent)
        } catch (ex: Exception) {
            // fallback: open app notification settings
            val fallback = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.parse("package:$packageName")
            }
            startActivity(fallback)
        }
    }
}
