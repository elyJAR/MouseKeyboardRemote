package com.antigravity.mousekeyboard.protocol

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer

object PacketCodec {

    // Fast binary encoding for UDP pointer delta packets (8 bytes: Float dx, Float dy)
    fun encodePointerDelta(dx: Float, dy: Float): ByteArray {
        val buffer = ByteBuffer.allocate(9)
        buffer.put(0x01.toByte()) // Marker for POINTER_DELTA
        buffer.putFloat(dx)
        buffer.putFloat(dy)
        return buffer.array()
    }

    fun decodePointerDelta(bytes: ByteArray, length: Int): PointerDeltaPacket? {
        if (length < 9 || bytes[0] != 0x01.toByte()) return null
        val buffer = ByteBuffer.wrap(bytes, 1, 8)
        val dx = buffer.getFloat()
        val dy = buffer.getFloat()
        return PointerDeltaPacket(dx, dy)
    }

    // JSON encoding for TCP command packets
    fun encodeJson(packet: RemotePacket): String {
        val json = JSONObject()
        json.put("type", packet.type.name)

        when (packet) {
            is ClickPacket -> {
                json.put("action", packet.action.name)
                json.put("x", packet.x.toDouble())
                json.put("y", packet.y.toDouble())
            }
            is ScrollPacket -> {
                json.put("deltaX", packet.deltaX.toDouble())
                json.put("deltaY", packet.deltaY.toDouble())
            }
            is KeyEventPacket -> {
                json.put("keyCode", packet.keyCode)
                json.put("keyAction", packet.keyAction)
            }
            is TextInputPacket -> {
                json.put("text", packet.text)
                json.put("clearExisting", packet.clearExisting)
            }
            is AppLaunchPacket -> {
                json.put("packageName", packet.packageName)
            }
            is PinPairPacket -> {
                json.put("pin", packet.pin)
            }
            is PinResponsePacket -> {
                json.put("success", packet.success)
                json.put("message", packet.message)
            }
            is AppListResponsePacket -> {
                val array = JSONArray()
                packet.apps.forEach { app ->
                    val appJson = JSONObject()
                    appJson.put("appName", app.appName)
                    appJson.put("packageName", app.packageName)
                    array.put(appJson)
                }
                json.put("apps", array)
            }
            else -> {}
        }
        return json.toString()
    }

    fun decodeJson(jsonStr: String): RemotePacket? {
        return try {
            val json = JSONObject(jsonStr)
            val typeStr = json.getString("type")
            val type = PacketType.valueOf(typeStr)

            when (type) {
                PacketType.CLICK -> {
                    val action = ClickAction.valueOf(json.getString("action"))
                    val x = json.optDouble("x", -1.0).toFloat()
                    val y = json.optDouble("y", -1.0).toFloat()
                    ClickPacket(action, x, y)
                }
                PacketType.SCROLL -> {
                    val deltaX = json.getDouble("deltaX").toFloat()
                    val deltaY = json.getDouble("deltaY").toFloat()
                    ScrollPacket(deltaX, deltaY)
                }
                PacketType.KEY_EVENT -> {
                    val keyCode = json.getInt("keyCode")
                    val keyAction = json.optInt("keyAction", 0)
                    KeyEventPacket(keyCode, keyAction)
                }
                PacketType.TEXT_INPUT -> {
                    val text = json.getString("text")
                    val clearExisting = json.optBoolean("clearExisting", false)
                    TextInputPacket(text, clearExisting)
                }
                PacketType.APP_LAUNCH -> {
                    val packageName = json.getString("packageName")
                    AppLaunchPacket(packageName)
                }
                PacketType.PIN_PAIR -> {
                    val pin = json.getString("pin")
                    PinPairPacket(pin)
                }
                PacketType.PIN_RESPONSE -> {
                    val success = json.getBoolean("success")
                    val message = json.getString("message")
                    PinResponsePacket(success, message)
                }
                PacketType.APP_LIST_RESPONSE -> {
                    val array = json.getJSONArray("apps")
                    val list = mutableListOf<AppInfoItem>()
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        list.add(AppInfoItem(item.getString("appName"), item.getString("packageName")))
                    }
                    AppListResponsePacket(list)
                }
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
