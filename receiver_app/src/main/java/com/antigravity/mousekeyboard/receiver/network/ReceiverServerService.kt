package com.antigravity.mousekeyboard.receiver.network

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.antigravity.mousekeyboard.protocol.AppInfoItem
import com.antigravity.mousekeyboard.protocol.AppListResponsePacket
import com.antigravity.mousekeyboard.protocol.AppLaunchPacket
import com.antigravity.mousekeyboard.protocol.ClickAction
import com.antigravity.mousekeyboard.protocol.ClickPacket
import com.antigravity.mousekeyboard.protocol.KeyEventPacket
import com.antigravity.mousekeyboard.protocol.NetworkConstants
import com.antigravity.mousekeyboard.protocol.PacketCodec
import com.antigravity.mousekeyboard.protocol.PacketType
import com.antigravity.mousekeyboard.protocol.PinPairPacket
import com.antigravity.mousekeyboard.protocol.PinResponsePacket
import com.antigravity.mousekeyboard.protocol.PointerDeltaPacket
import com.antigravity.mousekeyboard.protocol.ScrollPacket
import com.antigravity.mousekeyboard.protocol.TextInputPacket
import com.antigravity.mousekeyboard.receiver.CursorOverlayManager
import com.antigravity.mousekeyboard.receiver.RemoteAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random

class ReceiverServerService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var udpSocket: DatagramSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private var isRunning = false

    private lateinit var nsdAdvertiser: NsdAdvertiser
    private lateinit var overlayManager: CursorOverlayManager

    var currentPinCode: String = generatePin()
        private set
    var isClientConnected: Boolean = false
        private set
    var onStatusChanged: ((Boolean) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): ReceiverServerService = this@ReceiverServerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        nsdAdvertiser = NsdAdvertiser(this)
        overlayManager = CursorOverlayManager(this)

        startForegroundNotification()
        startServer()
    }

    private fun generatePin(): String {
        return String.format("%04d", Random.nextInt(10000))
    }

    private fun startForegroundNotification() {
        val channelId = "remote_receiver_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TV Remote Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Android TV Remote Active")
            .setContentText("Listening for mouse and keyboard controller...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1001, notification)
    }

    fun startServer() {
        if (isRunning) return
        isRunning = true

        nsdAdvertiser.startAdvertising(NetworkConstants.TCP_PORT)

        // Start UDP listener for low-latency pointer deltas
        serviceScope.launch {
            listenUdp()
        }

        // Start TCP listener for command packets
        serviceScope.launch {
            listenTcp()
        }
    }

    private suspend fun listenUdp() = withContext(Dispatchers.IO) {
        try {
            udpSocket = DatagramSocket(NetworkConstants.UDP_PORT)
            val buffer = ByteArray(256)

            while (isRunning) {
                val packet = DatagramPacket(buffer, buffer.size)
                udpSocket?.receive(packet)

                val delta = PacketCodec.decodePointerDelta(packet.data, packet.length)
                delta?.let {
                    withContext(Dispatchers.Main) {
                        overlayManager.showCursor()
                        overlayManager.moveCursor(it.dx, it.dy)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun listenTcp() = withContext(Dispatchers.IO) {
        try {
            tcpServerSocket = ServerSocket(NetworkConstants.TCP_PORT)

            while (isRunning) {
                val socket = tcpServerSocket?.accept() ?: break
                handleTcpClient(socket)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleTcpClient(socket: Socket) {
        serviceScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)

                var authenticated = false

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val rawJson = line ?: break
                    val remotePacket = PacketCodec.decodeJson(rawJson) ?: continue

                    if (!authenticated) {
                        if (remotePacket is PinPairPacket) {
                            if (remotePacket.pin == currentPinCode) {
                                authenticated = true
                                isClientConnected = true
                                updateStatus(true)
                                val response = PacketCodec.encodeJson(
                                    PinResponsePacket(true, "PIN Verified Successfully")
                                )
                                writer.println(response)
                            } else {
                                val response = PacketCodec.encodeJson(
                                    PinResponsePacket(false, "Invalid PIN")
                                )
                                writer.println(response)
                            }
                        }
                        continue
                    }

                    // Process authenticated commands
                    processCommand(remotePacket, writer)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isClientConnected = false
                updateStatus(false)
            }
        }
    }

    private fun processCommand(packet: com.antigravity.mousekeyboard.protocol.RemotePacket, writer: PrintWriter) {
        val service = RemoteAccessibilityService.instance
        val (cx, cy) = overlayManager.getCursorPosition()

        when (packet) {
            is ClickPacket -> {
                if (service != null) {
                    when (packet.action) {
                        ClickAction.LEFT_CLICK -> {
                            overlayManager.setClickState(true)
                            service.injectTap(cx, cy)
                            overlayManager.setClickState(false)
                        }
                        ClickAction.RIGHT_CLICK -> {
                            service.performSystemAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        }
                        ClickAction.DOUBLE_CLICK -> {
                            service.injectDoubleTap(cx, cy)
                        }
                        ClickAction.PRESS_HOLD -> {
                            overlayManager.setClickState(true)
                        }
                        ClickAction.RELEASE -> {
                            overlayManager.setClickState(false)
                        }
                    }
                } else {
                    // Fallback to OK/ENTER key event when accessibility service is not yet enabled
                    if (packet.action == ClickAction.LEFT_CLICK) {
                        injectKeyFallback(KeyEvent.KEYCODE_DPAD_CENTER)
                    } else if (packet.action == ClickAction.RIGHT_CLICK) {
                        injectKeyFallback(KeyEvent.KEYCODE_BACK)
                    }
                }
            }
            is ScrollPacket -> {
                if (service != null) {
                    val scrollFactor = 50f
                    service.injectScroll(cx, cy, cx - packet.deltaX * scrollFactor, cy - packet.deltaY * scrollFactor)
                } else {
                    val dy = packet.deltaY
                    if (dy > 0) injectKeyFallback(KeyEvent.KEYCODE_DPAD_DOWN)
                    else if (dy < 0) injectKeyFallback(KeyEvent.KEYCODE_DPAD_UP)
                }
            }
            is KeyEventPacket -> {
                if (service != null) {
                    when (packet.keyCode) {
                        KeyEvent.KEYCODE_BACK -> service.performSystemAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        KeyEvent.KEYCODE_HOME -> service.performSystemAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        KeyEvent.KEYCODE_APP_SWITCH -> service.performSystemAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                        else -> injectKeyFallback(packet.keyCode)
                    }
                } else {
                    injectKeyFallback(packet.keyCode)
                }
            }
            is TextInputPacket -> {
                if (service != null) {
                    service.injectText(packet.text)
                } else {
                    injectTextFallback(packet.text)
                }
            }
            is AppLaunchPacket -> {
                val launchIntent = packageManager.getLaunchIntentForPackage(packet.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }
            is com.antigravity.mousekeyboard.protocol.RemotePacket -> {
                if (packet.type == PacketType.APP_LIST_REQUEST) {
                    val installedApps = getInstalledTvApps()
                    val response = PacketCodec.encodeJson(AppListResponsePacket(installedApps))
                    writer.println(response)
                }
            }
        }
    }

    private fun injectKeyFallback(keyCode: Int) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec("input keyevent $keyCode")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun injectTextFallback(text: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val escapedText = text.replace(" ", "%s")
                Runtime.getRuntime().exec("input text $escapedText")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getInstalledTvApps(): List<AppInfoItem> {
        val list = mutableListOf<AppInfoItem>()
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = packageManager.queryIntentActivities(mainIntent, 0)
        for (app in apps) {
            val name = app.loadLabel(packageManager).toString()
            val packageName = app.activityInfo.packageName
            list.add(AppInfoItem(name, packageName))
        }
        return list
    }

    private fun updateStatus(connected: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            onStatusChanged?.invoke(connected)
        }
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val host = addr.hostAddress
                        if (host != null && !host.contains(":")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unavailable"
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        nsdAdvertiser.stopAdvertising()
        udpSocket?.close()
        tcpServerSocket?.close()
        overlayManager.hideCursor()
    }
}
