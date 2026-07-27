package com.antigravity.mousekeyboard.controller.network

import com.antigravity.mousekeyboard.protocol.ClickAction
import com.antigravity.mousekeyboard.protocol.ClickPacket
import com.antigravity.mousekeyboard.protocol.KeyEventPacket
import com.antigravity.mousekeyboard.protocol.NetworkConstants
import com.antigravity.mousekeyboard.protocol.PacketCodec
import com.antigravity.mousekeyboard.protocol.PinPairPacket
import com.antigravity.mousekeyboard.protocol.PinResponsePacket
import com.antigravity.mousekeyboard.protocol.RemotePacket
import com.antigravity.mousekeyboard.protocol.ScrollPacket
import com.antigravity.mousekeyboard.protocol.TextInputPacket
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
import java.net.Socket

class ControllerClientManager {

    private val clientScope = CoroutineScope(Dispatchers.IO + Job())

    private var targetAddress: InetAddress? = null
    private var tcpSocket: Socket? = null
    private var tcpWriter: PrintWriter? = null
    private var tcpReader: BufferedReader? = null
    private var udpSocket: DatagramSocket? = null

    var isConnected = false
        private set
    var isPairingAuthenticated = false
        private set

    var onPairingResult: ((Boolean, String) -> Unit)? = null
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    fun connect(hostAddress: InetAddress) {
        disconnect()
        targetAddress = hostAddress

        clientScope.launch {
            try {
                udpSocket = DatagramSocket()
                tcpSocket = Socket(hostAddress, NetworkConstants.TCP_PORT)
                tcpWriter = PrintWriter(tcpSocket!!.getOutputStream(), true)
                tcpReader = BufferedReader(InputStreamReader(tcpSocket!!.getInputStream()))

                isConnected = true
                withContext(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke(true)
                }

                // Listen for TCP responses (e.g., PIN response, App List)
                listenTcpResponses()

            } catch (e: Exception) {
                e.printStackTrace()
                disconnect()
            }
        }
    }

    private suspend fun listenTcpResponses() = withContext(Dispatchers.IO) {
        try {
            var line: String?
            while (tcpReader?.readLine().also { line = it } != null) {
                val rawJson = line ?: break
                val packet = PacketCodec.decodeJson(rawJson) ?: continue

                if (packet is PinResponsePacket) {
                    isPairingAuthenticated = packet.success
                    withContext(Dispatchers.Main) {
                        onPairingResult?.invoke(packet.success, packet.message)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            disconnect()
        }
    }

    fun sendPin(pin: String) {
        sendTcpPacket(PinPairPacket(pin))
    }

    fun sendPointerDelta(dx: Float, dy: Float) {
        val address = targetAddress ?: return
        clientScope.launch {
            try {
                val bytes = PacketCodec.encodePointerDelta(dx, dy)
                val packet = DatagramPacket(
                    bytes,
                    bytes.size,
                    address,
                    NetworkConstants.UDP_PORT
                )
                udpSocket?.send(packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendClick(action: ClickAction) {
        sendTcpPacket(ClickPacket(action))
    }

    fun sendScroll(deltaX: Float, deltaY: Float) {
        sendTcpPacket(ScrollPacket(deltaX, deltaY))
    }

    fun sendKeyEvent(keyCode: Int) {
        sendTcpPacket(KeyEventPacket(keyCode))
    }

    fun sendText(text: String) {
        sendTcpPacket(TextInputPacket(text))
    }

    private fun sendTcpPacket(packet: RemotePacket) {
        clientScope.launch {
            try {
                val jsonStr = PacketCodec.encodeJson(packet)
                tcpWriter?.println(jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        clientScope.launch {
            try {
                udpSocket?.close()
                tcpWriter?.close()
                tcpReader?.close()
                tcpSocket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isConnected = false
                isPairingAuthenticated = false
                udpSocket = null
                tcpSocket = null
                tcpWriter = null
                tcpReader = null
                withContext(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke(false)
                }
            }
        }
    }
}
