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
import androidx.appcompat.app.AppCompatActivity
import com.antigravity.mousekeyboard.receiver.R
import com.antigravity.mousekeyboard.receiver.RemoteAccessibilityService
import com.antigravity.mousekeyboard.receiver.network.ReceiverServerService

class MainActivity : AppCompatActivity() {

    private var serverService: ReceiverServerService? = null
    private var isBound = false

    private lateinit var tvStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var tvPinCode: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button

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

        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // Start & Bind Service
        val serviceIntent = Intent(this, ReceiverServerService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
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
