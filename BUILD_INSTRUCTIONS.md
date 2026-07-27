# Dual-APK Android Mouse & Keyboard Remote System Guide

## 1. Project Structure Overview
* **`:shared_protocol`**: Shared Kotlin library containing data packets and binary/JSON encoders.
* **`:receiver_app`**: Android TV application (Android 9+ / minSdk 28) running the overlay cursor and accessibility gesture server.
* **`:controller_app`**: Handheld mobile application (minSdk 24) running the multi-touch trackpad, D-Pad, and soft keyboard relay.

---

## 2. Building the APKs

### Command Line Build
Open a terminal in the project directory `Mouse_keyboard/`:

```powershell
# Build both APKs
./gradlew assembleDebug
```
Output locations:
- `receiver_app/build/outputs/apk/debug/receiver_app-debug.apk`
- `controller_app/build/outputs/apk/debug/controller_app-debug.apk`

---

## 3. Workarounds for Older / Custom Android TV Devices

On older Android TV boxes (Android 7 / 8 / 9 or customized TV boxes like Rockchip/Amlogic/Allwinner), standard Android settings screens for **Accessibility** and **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`)** may be missing or hidden.

We provide **3 Built-in Workarounds**:

### Workaround 1: Auto-Grant via Root (1-Click on TV)
If your TV box is rooted (common on TV boxes):
1. Open the Receiver App on your TV.
2. Click **⚡ Auto-Grant Permissions (Root Workaround)**.
3. The app executes `su` root shell commands to grant both Accessibility and Overlay permissions automatically.

### Workaround 2: Instant ADB Bypass Commands (From PC or Mobile)
Connect to your TV via ADB over Wi-Fi (`adb connect <TV_IP_ADDRESS>:5555`) and run these commands once:

```bash
# 1. Grant Overlay Permission (Floating Pointer)
adb shell pm grant com.antigravity.mousekeyboard.receiver android.permission.SYSTEM_ALERT_WINDOW
adb shell appops set com.antigravity.mousekeyboard.receiver SYSTEM_ALERT_WINDOW allow

# 2. Enable Accessibility Service (Gesture & Input Injection)
adb shell settings put secure enabled_accessibility_services com.antigravity.mousekeyboard.receiver/.RemoteAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

### Workaround 3: Fallback D-Pad & Keyboard Remote Mode (No Overlay Required)
If `SYSTEM_ALERT_WINDOW` (Display over other apps) cannot be granted at all on your TV model:
- You do **NOT** need the mouse pointer overlay to control your TV!
- The Controller app's **D-Pad Mode** (Up, Down, Left, Right, OK, Back, Home, Recents, Volume) and **Keyboard Relay Mode** work out of the box without requiring overlay permissions!
