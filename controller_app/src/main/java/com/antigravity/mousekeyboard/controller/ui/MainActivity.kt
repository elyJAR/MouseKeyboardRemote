package com.antigravity.mousekeyboard.controller.ui

import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.antigravity.mousekeyboard.controller.R
import com.antigravity.mousekeyboard.controller.network.ControllerClientManager
import com.antigravity.mousekeyboard.controller.network.NsdDiscoverer
import com.antigravity.mousekeyboard.protocol.ClickAction
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    private lateinit var clientManager: ControllerClientManager
    private lateinit var nsdDiscoverer: NsdDiscoverer

    private lateinit var tvConnectionStatus: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnManualIp: Button
    private lateinit var tabLayout: TabLayout

    private lateinit var containerTrackpad: LinearLayout
    private lateinit var containerDpad: LinearLayout
    private lateinit var containerKeyboard: LinearLayout

    private lateinit var trackpadView: TrackpadView
    private lateinit var btnLeftClick: Button
    private lateinit var btnRightClick: Button

    private lateinit var btnDpadUp: Button
    private lateinit var btnDpadDown: Button
    private lateinit var btnDpadLeft: Button
    private lateinit var btnDpadRight: Button
    private lateinit var btnDpadCenter: Button
    private lateinit var btnHome: Button
    private lateinit var btnBack: Button

    private lateinit var etInput: EditText
    private lateinit var btnSendText: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clientManager = ControllerClientManager()
        nsdDiscoverer = NsdDiscoverer(this)

        initViews()
        setupListeners()
        setupNetworkCallbacks()
    }

    private fun initViews() {
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        btnConnect = findViewById(R.id.btnConnect)
        btnManualIp = findViewById(R.id.btnManualIp)
        tabLayout = findViewById(R.id.tabLayout)

        containerTrackpad = findViewById(R.id.containerTrackpad)
        containerDpad = findViewById(R.id.containerDpad)
        containerKeyboard = findViewById(R.id.containerKeyboard)

        trackpadView = findViewById(R.id.trackpadView)
        btnLeftClick = findViewById(R.id.btnLeftClick)
        btnRightClick = findViewById(R.id.btnRightClick)

        btnDpadUp = findViewById(R.id.btnDpadUp)
        btnDpadDown = findViewById(R.id.btnDpadDown)
        btnDpadLeft = findViewById(R.id.btnDpadLeft)
        btnDpadRight = findViewById(R.id.btnDpadRight)
        btnDpadCenter = findViewById(R.id.btnDpadCenter)
        btnHome = findViewById(R.id.btnHome)
        btnBack = findViewById(R.id.btnBack)

        etInput = findViewById(R.id.etInput)
        btnSendText = findViewById(R.id.btnSendText)
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            if (clientManager.isConnected) {
                clientManager.disconnect()
            } else {
                tvConnectionStatus.text = getString(R.string.status_searching)
                nsdDiscoverer.startDiscovery()
            }
        }

        btnManualIp.setOnClickListener {
            showManualIpDialog()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showContainer(containerTrackpad)
                    1 -> showContainer(containerDpad)
                    2 -> showContainer(containerKeyboard)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Trackpad View Callbacks
        trackpadView.onPointerDelta = { dx, dy ->
            if (clientManager.isPairingAuthenticated) {
                clientManager.sendPointerDelta(dx, dy)
            }
        }

        trackpadView.onClick = { isLeftClick ->
            if (clientManager.isPairingAuthenticated) {
                val action = if (isLeftClick) ClickAction.LEFT_CLICK else ClickAction.RIGHT_CLICK
                clientManager.sendClick(action)
            }
        }

        trackpadView.onScroll = { dx, dy ->
            if (clientManager.isPairingAuthenticated) {
                clientManager.sendScroll(dx, dy)
            }
        }

        btnLeftClick.setOnClickListener {
            clientManager.sendClick(ClickAction.LEFT_CLICK)
        }

        btnRightClick.setOnClickListener {
            clientManager.sendClick(ClickAction.RIGHT_CLICK)
        }

        // D-Pad Controls
        btnDpadUp.setOnClickListener { clientManager.sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP) }
        btnDpadDown.setOnClickListener { clientManager.sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN) }
        btnDpadLeft.setOnClickListener { clientManager.sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT) }
        btnDpadRight.setOnClickListener { clientManager.sendKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT) }
        btnDpadCenter.setOnClickListener { clientManager.sendKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER) }
        btnHome.setOnClickListener { clientManager.sendKeyEvent(KeyEvent.KEYCODE_HOME) }
        btnBack.setOnClickListener { clientManager.sendKeyEvent(KeyEvent.KEYCODE_BACK) }

        // Keyboard Relay
        btnSendText.setOnClickListener {
            val text = etInput.text.toString()
            if (text.isNotEmpty()) {
                clientManager.sendText(text)
                etInput.text.clear()
            }
        }
    }

    private fun showManualIpDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "e.g. 192.168.43.1 or 192.168.1.15"
        }

        AlertDialog.Builder(this)
            .setTitle("Manual / Hotspot IP Connection")
            .setMessage("Enter the local IP address displayed on your Android TV screen:")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val ipStr = input.text.toString().trim()
                if (ipStr.isNotEmpty()) {
                    tvConnectionStatus.text = "Connecting to $ipStr..."
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val addr = InetAddress.getByName(ipStr)
                            clientManager.connect(addr)
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Invalid IP Address", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupNetworkCallbacks() {
        nsdDiscoverer.onDeviceDiscovered = { hostAddress, _ ->
            runOnUiThread {
                nsdDiscoverer.stopDiscovery()
                clientManager.connect(hostAddress)
            }
        }

        clientManager.onConnectionStateChanged = { connected ->
            runOnUiThread {
                if (connected) {
                    tvConnectionStatus.text = "Connected - Verifying PIN..."
                    btnConnect.text = getString(R.string.disconnect_button)
                    showPinDialog()
                } else {
                    tvConnectionStatus.text = getString(R.string.status_disconnected)
                    btnConnect.text = getString(R.string.connect_button)
                }
            }
        }

        clientManager.onPairingResult = { success, message ->
            runOnUiThread {
                if (success) {
                    tvConnectionStatus.text = getString(R.string.status_connected)
                    Toast.makeText(this, "Paired Successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    tvConnectionStatus.text = "PIN Error: $message"
                    Toast.makeText(this, "PIN Error: $message", Toast.LENGTH_LONG).show()
                    showPinDialog()
                }
            }
        }
    }

    private fun showPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter 4-digit PIN"
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.enter_pin_title)
            .setMessage(R.string.enter_pin_hint)
            .setView(input)
            .setPositiveButton("Verify") { _, _ ->
                val pin = input.text.toString()
                clientManager.sendPin(pin)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                clientManager.disconnect()
            }
            .show()
    }

    private fun showContainer(target: View) {
        containerTrackpad.visibility = View.GONE
        containerDpad.visibility = View.GONE
        containerKeyboard.visibility = View.GONE
        target.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        nsdDiscoverer.stopDiscovery()
        clientManager.disconnect()
    }
}
