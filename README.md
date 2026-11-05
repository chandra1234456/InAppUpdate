# Android In-App Update – Complete Setup Guide (Local Windows Python Server)

## 1. Project Overview

This project implements an in-app APK update system over a local Wi-Fi network.

The application:

- Checks a local update server for new versions.
- Downloads the latest APK with a progress dialog.
- Verifies the APK using SHA-256.
- Prompts the user to install the update.
- Supports optional forced updates.
- Ignores double clicks (only one check/download runs at a time).

> **Debug builds only.** The updater is controlled by the `LOCAL_UPDATE_ENABLED` build flag in `app/build.gradle.kts`: `true` for debug, `false` for release (no network calls, no update UI).

### Requirements

- Android Studio
- Android phone (USB debugging enabled)
- Windows laptop
- Python installed
- Laptop and phone on the same Wi-Fi network
- Android project with FileProvider configured (already done in this project)

### How it works

1. The app starts (or the user taps **Check for Updates**) and requests `http://<laptop-ip>:8080/version.json`.
2. If `versionCode` in the JSON is higher than the installed one, an update dialog appears.
3. **UPDATE NOW** downloads the APK with a progress bar.
4. The SHA-256 of the file is compared with the value in `version.json`.
5. If it matches, the Android package installer opens.

---

## 2. Verify Python Installation

Open PowerShell and run:

```powershell
python --version
```

Expected output (version may differ):

```text
Python 3.15.2
```

If Python is not recognized, install it from https://www.python.org/downloads/ and enable **Add Python to PATH** during installation.

---

## 3. Signing Key (same key for every build)

The keystore `inappupdate.jks` and `keystore.properties` live in the project root and are wired into `app/build.gradle.kts` for both debug and release builds.

- Back up both files. If they are lost, installed apps can no longer be updated.
- Never commit them (they are in `.gitignore`).
- Every APK must be signed with this same key, otherwise Android refuses the update (the installer may even hang at "Installing…").

To recreate the keystore:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -keystore inappupdate.jks -alias inappupdate -keyalg RSA -keysize 2048 -validity 10000
```

`keystore.properties`:

```text
storeFile=inappupdate.jks
storePassword=YOUR_PASSWORD
keyAlias=inappupdate
keyPassword=YOUR_PASSWORD
```

---

## 4. Install Version 1 on Android

1. Open the project in Android Studio.
2. Select the **debug** build variant.
3. Make sure `app/build.gradle.kts` has:

```kotlin
versionCode = 1
versionName = "1.0"
```

4. Connect your Android phone using USB debugging.
5. Click **Run** in Android Studio.
6. Confirm that version 1 is installed.

**Important:** Version 1 must remain installed for the update test. If the phone has an older build signed with a different key, uninstall it first.

---

## 5. Configure Version 2

Open `app/build.gradle.kts` and change:

```kotlin
versionCode = 1
versionName = "1.0"
```

to:

```kotlin
versionCode = 2
versionName = "2.0"
```

Save the file.

---

## 6. Build Version 2 APK

In Android Studio: **Build → Build APK(s)** and wait for it to finish.

Or use PowerShell:

```powershell
cd D:\private\InAppUpdate
.\gradlew assembleDebug
```

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

---

## 7. Create Update Server Folder

```powershell
mkdir C:\update-server
copy D:\private\InAppUpdate\app\build\outputs\apk\debug\app-debug.apk C:\update-server\app-v2.apk
```

The folder should contain:

```text
C:\update-server
    |
    |-- app-v2.apk
```

---

## 8. Generate SHA-256 Checksum

```powershell
(Get-FileHash "C:\update-server\app-v2.apk" -Algorithm SHA256).Hash.ToLower()
```

Example output:

```text
e8d32b2988011aa49517131dccfde7bf143ca262895bfae4430077b8c1fa71d8
```

Copy the complete hash.

**Important:** every new APK requires a new SHA-256 checksum.

---

## 9. Create version.json

```powershell
notepad C:\update-server\version.json
```

Paste:

```json
{
  "versionCode": 2,
  "versionName": "2.0",
  "apkFile": "app-v2.apk",
  "message": "New update available!",
  "sha256": "PASTE_HASH_HERE",
  "forceUpdate": false
}
```

Replace `PASTE_HASH_HERE` with the real checksum and save.

| Field | Meaning |
|---|---|
| `versionCode` | Must be higher than the installed app's |
| `versionName` | Shown in the update dialog |
| `apkFile` | File name in the server folder (letters, digits, `.`, `_`, `-` only) |
| `message` | Text shown in the dialog |
| `sha256` | Lowercase hash of the APK |
| `forceUpdate` | `true` hides the "LATER" button |

The folder should now contain:

```text
C:\update-server
    |
    |-- app-v2.apk
    |
    |-- version.json
```

---

## 10. Find Windows Laptop IP Address

```powershell
ipconfig
```

Find `Wireless LAN adapter Wi-Fi` and read the `IPv4 Address`, for example:

```text
10.255.152.22
```

Your address may differ, and it can change whenever you reconnect (especially on a phone hotspot).

---

## 11. Configure AppUpdater.kt

Open:

```text
app/src/main/java/com/chandra/InAppUpdate/AppUpdater.kt
```

Find:

```kotlin
private val server =
    "http://192.168.1.4:8080"
```

Replace with your laptop's IP:

```kotlin
private val server =
    "http://10.255.152.22:8080"
```

The update check uses:

```kotlin
val connection =
    URL("$server/version.json")
        .openConnection() as HttpURLConnection
```

giving the URL `http://10.255.152.22:8080/version.json`. Save the file.

---

## 12. Rebuild and Reinstall Version 1

Because the server IP changed, rebuild version 1 with the updated address.

Set:

```kotlin
versionCode = 1
versionName = "1.0"
```

Build and install version 1 on the phone. Do **not** install version 2 manually. The phone must remain on version 1 for the update test.

---

## 13. Configure Windows Firewall

Open PowerShell **as Administrator** and run once:

```powershell
New-NetFirewallRule -DisplayName "UpdateServer" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Any
```

This allows incoming connections to the local update server. `-Profile Any` is needed if your Wi-Fi network is set to "Public".

---

## 14. Start Python HTTP Server

Open a new PowerShell window:

```powershell
cd C:\update-server
python -m http.server 8080 --bind 0.0.0.0
```

Expected output:

```text
Serving HTTP on 0.0.0.0 port 8080
```

Keep this window open. Each request from the phone appears here, so you can see whether it connects.

---

## 15. Test Server from Android Phone

1. Connect the phone to the same Wi-Fi as the laptop.
2. Open Chrome on the phone.
3. Enter:

```text
http://10.255.152.22:8080/version.json
```

The browser should display the JSON with `"versionCode": 2` and `"versionName": "2.0"`.

Also test the APK URL:

```text
http://10.255.152.22:8080/app-v2.apk
```

The APK should download.

---

## 16. Test In-App Update

1. Open the application on the phone. It checks for updates automatically, or tap **Check for Updates**.
2. The update dialog shows version 2.0.
3. Tap **UPDATE NOW**.
4. A **Downloading Update** dialog shows the progress.
5. Android may ask permission to install unknown apps from your application. Allow it.
6. Return to the application.
7. Tap **Install** and confirm.

Expected result:

```text
Installed Version: 2.0
versionCode: 2
```

---

## 17. Troubleshooting

| Problem | Possible solution |
|---|---|
| Update check failed | Check laptop IP, Wi-Fi and firewall |
| No update dialog | `versionCode` in `version.json` must be higher than the installed one |
| Checksum mismatch | Generate a new SHA-256 hash for the current APK |
| APK download failed | Verify the Python server is running and `apkFile` exists |
| Connection refused | Check firewall rule and server port |
| Installation failed / installer stuck at "Installing…" | APK signature differs from the installed app. Uninstall once, reinstall v1 built with `inappupdate.jks`. Confirm with `adb install -r C:\update-server\app-v2.apk` |
| App not installing | Check "Install unknown apps" permission; turn off Play Protect scanning if it hangs |
| Update URL not working | Check the IP address in `AppUpdater.kt` |
| Gradle: "Failed to resolve androidx.compose..." | Turn off Gradle offline mode, then `.\gradlew --refresh-dependencies` |
| Server log shows no requests | Check firewall, network profile, IP address and same Wi-Fi |

Useful diagnostics (phone connected over USB):

```powershell
adb devices
adb shell dumpsys package com.chandra.InAppUpdate | findstr versionCode
adb logcat -d | findstr "PackageInstaller INSTALL_FAILED"
```

---

## 18. How to Release Version 3, 4, 5...

For every new release:

1. Change the version numbers in `app/build.gradle.kts`.
2. Build the new APK.
3. Copy the APK to `C:\update-server`.
4. Generate a new SHA-256 checksum.
5. Update `version.json` with the correct `versionCode`, `versionName`, APK filename and checksum.
6. Keep the Python server running.
7. Open the app on the phone and check for updates.

Example for version 3:

```json
{
  "versionCode": 3,
  "versionName": "3.0",
  "apkFile": "app-v3.apk",
  "message": "Version 3 is available!",
  "sha256": "NEW_SHA256_HASH",
  "forceUpdate": false
}
```

**Important:** never decrease `versionCode`. Each release must be higher than the previous one.

---

## 19. Important Notes

- Use the same signing key for all APK updates.
- Keep the application package name unchanged (`com.chandra.InAppUpdate`).
- Keep `versionCode` increasing.
- Generate a new SHA-256 checksum for every new APK.
- Keep the update server running during testing.
- The phone and laptop must be able to communicate over the network.
- The app allows cleartext HTTP (`usesCleartextTraffic`) for the local server. This is acceptable on a trusted local network only.
- Python's built-in HTTP server is for local testing, not production. For production use HTTPS and a secured update server.
- Release builds disable the updater completely.
- Never commit `inappupdate.jks` or `keystore.properties`.

---

## 20. Project Folder Structure

```text
InAppUpdate/
|
|-- app/
|   |-- build.gradle.kts
|   |-- src/
|       |-- main/
|           |-- AndroidManifest.xml
|           |-- java/
|               |-- com/chandra/InAppUpdate/
|                   |-- AppUpdater.kt
|                   |-- MainActivity.kt
|
|-- inappupdate.jks
|-- keystore.properties
|-- README.md

C:\update-server\          (outside the project)
|
|-- app-v2.apk
|-- version.json
```

---

End of Setup Guide – Android In-App Update, Local Windows Python Server.
