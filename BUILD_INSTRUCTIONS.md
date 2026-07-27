# Dual-APK Android Mouse & Keyboard Remote Build & Installation Guide

## 1. Project Structure Overview
* **`:shared_protocol`**: Shared Kotlin library containing data packets and binary/JSON encoders.
* **`:receiver_app`**: Android TV application (Android 9+ / minSdk 28) running the overlay cursor and accessibility gesture server.
* **`:controller_app`**: Handheld mobile application (minSdk 24) running the multi-touch trackpad, D-pad, and soft keyboard relay.

---

## 2. Building the APKs

### Prerequisites
* **Android Studio**: Jellyfish (2023.3.1) or newer recommended.
* **Android SDK**: API level 34 (Android 14) compile SDK installed.
* **JDK**: Version 17 or higher.

### Command Line Build
Open a terminal in the project directory `Mouse_keyboard/`:

#### Build Receiver APK (Android TV Target)
```powershell
./gradlew :receiver_app:assembleDebug
```
Output location:
`receiver_app/build/outputs/apk/debug/receiver_app-debug.apk`

#### Build Controller APK (Phone / Mobile Target)
```powershell
./gradlew :controller_app:assembleDebug
```
Output location:
`controller_app/build/outputs/apk/debug/controller_app-debug.apk`

#### Build Both APKs Simultaneously
```powershell
./gradlew assembleDebug
```

---

## 3. Installation & First-Time Onboarding

### A. Installing Receiver APK on Android TV
1. Install `receiver_app-debug.apk` onto your Android TV box or Android 9 TV device (via ADB over Wi-Fi or USB drive):
   ```powershell
   adb connect <TV_IP_ADDRESS>:5555
   adb install -r receiver_app/build/outputs/apk/debug/receiver_app-debug.apk
   ```
2. Launch **TV Remote Receiver** on Android TV.
3. **Grant Required Permissions**:
   * Click **Enable Accessibility Service** -> Turn ON **TV Remote Receiver** under Accessibility.
   * Click **Enable Display Over Other Apps** -> Allow permission for **TV Remote Receiver**.
4. Note down the **Local IP Address** and 4-digit **Pairing PIN Code** displayed on the TV dashboard.

### B. Installing Controller APK on Mobile Phone
1. Install `controller_app-debug.apk` on your handheld Android phone:
   ```powershell
   adb install -r controller_app/build/outputs/apk/debug/controller_app-debug.apk
   ```
2. Ensure phone and Android TV are connected to the same local Wi-Fi network.
3. Open **Android Remote Controller** on your phone.
4. Tap **Connect to TV**. The app will auto-discover the TV via mDNS.
5. Enter the 4-digit PIN displayed on your TV screen to pair and start controlling the TV cursor, keyboard, and D-pad!
