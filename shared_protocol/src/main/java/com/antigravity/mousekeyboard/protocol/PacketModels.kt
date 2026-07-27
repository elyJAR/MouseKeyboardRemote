package com.antigravity.mousekeyboard.protocol

enum class PacketType {
    POINTER_DELTA,
    CLICK,
    SCROLL,
    KEY_EVENT,
    TEXT_INPUT,
    APP_LAUNCH,
    PIN_PAIR,
    PIN_RESPONSE,
    APP_LIST_REQUEST,
    APP_LIST_RESPONSE
}

enum class ClickAction {
    LEFT_CLICK,
    RIGHT_CLICK,
    DOUBLE_CLICK,
    PRESS_HOLD,
    RELEASE
}

sealed class RemotePacket(val type: PacketType)

data class PointerDeltaPacket(
    val dx: Float,
    val dy: Float
) : RemotePacket(PacketType.POINTER_DELTA)

data class ClickPacket(
    val action: ClickAction,
    val x: Float = -1f,
    val y: Float = -1f
) : RemotePacket(PacketType.CLICK)

data class ScrollPacket(
    val deltaX: Float,
    val deltaY: Float
) : RemotePacket(PacketType.SCROLL)

data class KeyEventPacket(
    val keyCode: Int,
    val keyAction: Int = 0 // 0 = ACTION_DOWN, 1 = ACTION_UP
) : RemotePacket(PacketType.KEY_EVENT)

data class TextInputPacket(
    val text: String,
    val clearExisting: Boolean = false
) : RemotePacket(PacketType.TEXT_INPUT)

data class AppLaunchPacket(
    val packageName: String
) : RemotePacket(PacketType.APP_LAUNCH)

data class PinPairPacket(
    val pin: String
) : RemotePacket(PacketType.PIN_PAIR)

data class PinResponsePacket(
    val success: Boolean,
    val message: String
) : RemotePacket(PacketType.PIN_RESPONSE)

data class AppInfoItem(
    val appName: String,
    val packageName: String
)

data class AppListResponsePacket(
    val apps: List<AppInfoItem>
) : RemotePacket(PacketType.APP_LIST_RESPONSE)

object NetworkConstants {
    const val NSD_SERVICE_TYPE = "_android-remote._tcp."
    const val NSD_SERVICE_NAME = "AndroidTVRemote"
    const val TCP_PORT = 8889
    const val UDP_PORT = 8888
}
