package com.antigravity.mousekeyboard.receiver.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.antigravity.mousekeyboard.receiver.R
import com.antigravity.mousekeyboard.receiver.RemoteAccessibilityService
import com.antigravity.mousekeyboard.receiver.network.ReceiverServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var serverService: ReceiverServerService? = null
    private var isBound = false

    private lateinit var tvStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var tvPinCode: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnAutoGrantRoot: Button

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ReceiverServerService.LocalBinder
            serverService = binder.getService()
            isBound = true

            updateUi()
            serverService?.onStatusChanged = { connected ->
                runOnUiThread {
                    if (connected) {
                        tvStatus.text = getString(R.string.status_connected)
                        tvStatus.setTextColor(getColor(R.color.tv_accent))
                    } else {
                        tvStatus.text = getString(R.string.status_disconnected)
                        tvStatus.setTextColor(getColor(R.color.white))
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serverService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvIpAddress = findViewById(R.id.tvIpAddress)
        tvPinCode = findViewById(R.id.tvPinCode)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnAutoGrantRoot = findViewById(R.id.btnAutoGrantRoot)

        btnAccessibility.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general settings if leanback TV settings crashes
                openGeneralSettings()
            }
        }

        btnOverlay.setOnClickListener {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to application details settings
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (ex: Exception) {
                    openGeneralSettings()
                }
            }
        }

        btnAutoGrantRoot.setOnClickListener {
            attemptRootAutoGrant()
        }

        // Start & Bind Service
        val serviceIntent = Intent(this, ReceiverServerService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun openGeneralSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Use ADB commands below to grant permissions on this TV", Toast.LENGTH_LONG).show()
        }
    }

    private fun attemptRootAutoGrant() {
        Toast.makeText(this, "Attempting Root Auto-Grant...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            try {
                val p = Runtime.getRuntime().exec("su")
                val os = p.outputStream
                val cmd1 = "pm grant $packageName android.permission.SYSTEM_ALERT_WINDOW\n"
                val cmd2 = "appops set $packageName SYSTEM_ALERT_WINDOW allow\n"
                val cmd3 = "settings put secure enabled_accessibility_services $packageName/.RemoteAccessibilityService\n"
                val cmd4 = "settings put secure accessibility_enabled 1\n"
                val cmd5 = "exit\n"

                os.write(cmd1.toByteArray())
                os.write(cmd2.toByteArray())
                os.write(cmd3.toByteArray())
                os.write(cmd4.toByteArray())
                os.write(cmd5.toByteArray())
                os.flush()
                p.waitFor()
                success = (p.exitValue() == 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@MainActivity, "Root permissions granted successfully!", Toast.LENGTH_LONG).show()
                    updateUi()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Root not detected. Please run the ADB shell commands shown below.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi() {
        serverService?.let { service ->
            tvIpAddress.text = service.getLocalIpAddress()
            tvPinCode.text = service.currentPinCode
        }

        val hasAccessibility = RemoteAccessibilityService.instance != null
        if (hasAccessibility) {
            btnAccessibility.text = "✓ Accessibility Service Enabled"
            btnAccessibility.isEnabled = false
        } else {
            btnAccessibility.text = getString(R.string.grant_accessibility)
            btnAccessibility.isEnabled = true
        }

        val hasOverlay = Settings.canDrawOverlays(this)
        if (hasOverlay) {
            btnOverlay.text = "✓ Overlay Permission Granted"
            btnOverlay.isEnabled = false
        } else {
            btnOverlay.text = getString(R.string.grant_overlay)
            btnOverlay.isEnabled = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
