package com.chandra.InAppUpdate

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

class AppUpdater(private val activity: Activity) {

    // Replace with your Windows laptop IP
    private val server =
        "http://10.255.152.22:8080"

    private val executor =
        Executors.newSingleThreadExecutor()

    private var pendingApk: File? = null

    // Guards against double clicks / duplicate requests
    private val isChecking = AtomicBoolean(false)
    private val isDownloading = AtomicBoolean(false)
    private var updateDialog: AlertDialog? = null
    private var progressDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    // STEP 1: CHECK UPDATE
    fun checkUpdate(manual: Boolean = false) {

        if (!BuildConfig.LOCAL_UPDATE_ENABLED) return

        // Ignore if a check/download is running or a dialog is already open
        if (isDownloading.get() || updateDialog?.isShowing == true) return
        if (!isChecking.compareAndSet(false, true)) return

        if (manual) {
            Toast.makeText(
                activity, "Checking for updates...", Toast.LENGTH_SHORT
            ).show()
        }

        executor.execute {

            try {
                val connection =
                    URL("$server/version.json")
                        .openConnection() as HttpURLConnection

                connection.connectTimeout = 5000
                connection.readTimeout = 10000

                val json = try {
                    JSONObject(
                        connection.inputStream.bufferedReader()
                            .use { it.readText() }
                    )
                } finally {
                    connection.disconnect()
                }

                val latestVersion =
                    json.getInt("versionCode")

                val currentVersion =
                    BuildConfig.VERSION_CODE

                if (latestVersion > currentVersion) {

                    val versionName =
                        json.getString("versionName")

                    val apkFile =
                        json.getString("apkFile")

                    val message =
                        json.optString(
                            "message",
                            "A new update is available!"
                        )

                    val sha256 =
                        json.getString("sha256")

                    val forceUpdate =
                        json.optBoolean(
                            "forceUpdate",
                            false
                        )

                    activity.runOnUiThread {

                        showUpdateDialog(
                            versionName,
                            apkFile,
                            message,
                            sha256,
                            forceUpdate
                        )
                    }
                } else if (manual) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "You are on the latest version",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {

                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Update check failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                isChecking.set(false)
            }
        }
    }

    // PROGRESS DIALOG
    private fun showProgressDialog() {

        val pad = (20 * activity.resources.displayMetrics.density).toInt()

        val bar = ProgressBar(
            activity, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            isIndeterminate = true
        }

        val text = TextView(activity).apply {
            text = "Starting download..."
            setPadding(0, pad / 2, 0, 0)
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(bar)
            addView(text)
        }

        progressBar = bar
        progressText = text

        progressDialog = AlertDialog.Builder(activity)
            .setTitle("Downloading Update")
            .setView(layout)
            .setCancelable(false)
            .create()

        progressDialog?.show()
    }

    private fun updateProgress(percent: Int, downloaded: Long, total: Long) {
        activity.runOnUiThread {
            progressBar?.apply {
                isIndeterminate = false
                progress = percent
            }
            progressText?.text =
                "$percent%  (${downloaded / 1024} KB / ${total / 1024} KB)"
        }
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        progressText = null
    }

    // STEP 2: UPDATE DIALOG
    private fun showUpdateDialog(
        version: String,
        apkFile: String,
        message: String,
        sha256: String,
        force: Boolean
    ) {

        val builder =
            AlertDialog.Builder(activity)

        builder.setTitle("New Update Available")

        builder.setMessage(
            "$message\n\nVersion: $version"
        )

        builder.setPositiveButton("UPDATE NOW") { _, _ ->
            downloadApk(apkFile, sha256)
        }

        if (!force) {
            builder.setNegativeButton("LATER", null)
        }

        builder.setCancelable(!force)

        updateDialog = builder.show()
    }

    // STEP 3: DOWNLOAD APK
    private fun downloadApk(
        apkFile: String,
        expectedHash: String
    ) {

        if (!isDownloading.compareAndSet(false, true)) return

        showProgressDialog()

        executor.execute {

            try {
                require(
                    apkFile.matches(
                        Regex("[A-Za-z0-9._-]+")
                    )
                ) {
                    "Invalid APK filename"
                }

                val folder = File(
                    activity.getExternalFilesDir(null),
                    "updates"
                )

                if (!folder.exists() && !folder.mkdirs()) {
                    throw Exception("Cannot create folder")
                }

                val apk = File(folder, "update.apk")

                val connection =
                    URL("$server/$apkFile")
                        .openConnection() as HttpURLConnection

                connection.connectTimeout = 10000
                connection.readTimeout = 30000

                try {
                    if (connection.responseCode != 200) {
                        throw Exception("Server download error")
                    }

                    val total = connection.contentLengthLong
                    var downloaded = 0L
                    var lastPercent = -1

                    connection.inputStream.use { input ->

                        FileOutputStream(apk).use { output ->

                            val buffer = ByteArray(8192)

                            while (true) {
                                val count =
                                    input.read(buffer)

                                if (count == -1) break

                                output.write(buffer, 0, count)
                                downloaded += count

                                if (total > 0) {
                                    val percent =
                                        (downloaded * 100 / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        updateProgress(
                                            percent, downloaded, total
                                        )
                                    }
                                }
                            }
                        }
                    }

                } finally {
                    connection.disconnect()
                }

                // STEP 4: VERIFY CHECKSUM
                val actualHash = calculateSHA256(apk)

                if (!actualHash.equals(
                        expectedHash,
                        ignoreCase = true
                    )
                ) {
                    apk.delete()
                    throw Exception("APK checksum mismatch")
                }

                activity.runOnUiThread {
                    dismissProgressDialog()
                    pendingApk = apk
                    installApk()
                }

            } catch (e: Exception) {

                activity.runOnUiThread {
                    dismissProgressDialog()
                    Toast.makeText(
                        activity,
                        "Download failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                isDownloading.set(false)
            }
        }
    }

    // STEP 5: SHA256
    private fun calculateSHA256(file: File): String {

        val digest =
            MessageDigest.getInstance("SHA-256")

        FileInputStream(file).use { input ->

            val buffer = ByteArray(8192)

            while (true) {

                val count = input.read(buffer)

                if (count == -1) break

                digest.update(buffer, 0, count)
            }
        }

        return digest.digest().joinToString("") {
            "%02x".format(it)
        }
    }

    // STEP 6: INSTALL APK
    fun installApk() {

        if (!BuildConfig.LOCAL_UPDATE_ENABLED) return

        val apk = pendingApk ?: return

        if (!apk.exists()) return

        if (
            Build.VERSION.SDK_INT >= 26 &&
            !activity.packageManager
                .canRequestPackageInstalls()
        ) {

            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )

            activity.startActivity(intent)

            Toast.makeText(
                activity,
                "Allow installation and return to the app.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {

            setDataAndType(
                uri,
                "application/vnd.android.package-archive"
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        pendingApk = null

        activity.startActivity(intent)
    }
}
