package rocks.gorjan.gokixp

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * User-triggered updater for WIN26.
 *
 * Privacy:
 * - No background polling.
 * - No analytics or device/user data is transmitted.
 * - Network access occurs only after the user presses Windows Update.
 *
 * Security:
 * - HTTPS only.
 * - The update checksum, package name, versionCode and signing certificate are verified.
 * - Android verifies the signing certificate again before replacing the installed app.
 */
class Win26Updater(private val activity: Activity) {

    data class UpdateInfo(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val sha256: String
    )

    companion object {
        private const val UPDATE_MANIFEST_URL =
            "https://github.com/dugasmarcel-wq/WIN26/releases/latest/download/update.json"
        private const val ALLOWED_APK_PREFIX =
            "https://github.com/dugasmarcel-wq/WIN26/releases/"
        private const val APK_FILE_NAME = "WIN26.apk"
        private const val MIME_APK = "application/vnd.android.package-archive"
    }

    fun checkForUpdates() {
        val progress = AlertDialog.Builder(activity)
            .setTitle("Windows Update")
            .setMessage("Checking for updates...")
            .setCancelable(false)
            .create()
        progress.show()

        Thread {
            try {
                val update = fetchManifest()
                val current = currentPackageInfo()
                val currentCode = current.longVersionCode

                activity.runOnUiThread {
                    progress.dismiss()
                    val installedName = current.versionName ?: "unknown"
                    if (update.versionCode <= currentCode) {
                        showActionDialog(
                            title = "Windows Update",
                            message =
                                "WIN26 is up to date.\n\n" +
                                    "Installed: $installedName (build $currentCode)\n" +
                                    "Latest: ${update.versionName} (build ${update.versionCode})",
                            positiveText = "OK"
                        )
                    } else {
                        showActionDialog(
                            title = "Windows Update",
                            message =
                                "A WIN26 update is available.\n\n" +
                                    "Installed: $installedName (build $currentCode)\n" +
                                    "Latest: ${update.versionName} (build ${update.versionCode})",
                            positiveText = "Download & Install",
                            onPositive = { beginInstall(update) },
                            negativeText = "Cancel"
                        )
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    progress.dismiss()
                    showError("Could not check for updates", e)
                }
            }
        }.start()
    }

    private fun beginInstall(update: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            showActionDialog(
                title = "Allow WIN26 updates",
                message =
                    "Android needs a one-time permission so WIN26 can install its own signed updates. " +
                        "Turn on “Allow from this source”, return to WIN26, then press Windows Update again.",
                positiveText = "Open Settings",
                onPositive = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                },
                negativeText = "Cancel"
            )
            return
        }

        val progress = AlertDialog.Builder(activity)
            .setTitle("Windows Update")
            .setMessage("Downloading update...")
            .setCancelable(false)
            .create()
        progress.show()

        Thread {
            try {
                val apk = downloadApk(update)
                verifyDownloadedApk(apk, update)
                activity.runOnUiThread {
                    progress.dismiss()
                    launchInstaller(apk)
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    progress.dismiss()
                    showError("Update download failed", e)
                }
            }
        }.start()
    }

    private fun fetchManifest(): UpdateInfo {
        val text = readHttpsText(UPDATE_MANIFEST_URL)
        val json = JSONObject(text)
        val versionCode = json.getLong("versionCode")
        val versionName = json.getString("versionName")
        val apkUrl = json.getString("apkUrl")
        val sha256 = json.getString("sha256").lowercase()

        require(versionCode > 0) { "Invalid update version" }
        require(versionName.isNotBlank()) { "Invalid update name" }
        require(
            apkUrl.startsWith(ALLOWED_APK_PREFIX) &&
                apkUrl.endsWith("/$APK_FILE_NAME") &&
                apkUrl.startsWith("https://")
        ) { "Update source is not approved" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid update checksum" }

        return UpdateInfo(versionCode, versionName, apkUrl, sha256)
    }

    private fun readHttpsText(urlString: String): String {
        val connection = openHttps(urlString)
        return try {
            require(connection.responseCode in 200..299) {
                "Update server returned HTTP ${connection.responseCode}"
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadApk(update: UpdateInfo): File {
        val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, APK_FILE_NAME)
        val temp = File(dir, "$APK_FILE_NAME.part")
        temp.delete()

        val connection = openHttps(update.apkUrl)
        try {
            require(connection.responseCode in 200..299) {
                "APK server returned HTTP ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }

        require(temp.length() > 0L) { "Downloaded APK is empty" }
        target.delete()
        require(temp.renameTo(target)) { "Could not finalize downloaded APK" }
        return target
    }

    private fun openHttps(urlString: String): HttpsURLConnection {
        val url = URL(urlString)
        require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS is required" }
        return (url.openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WIN26-Updater")
            setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, */*")
        }
    }

    private fun verifyDownloadedApk(apk: File, update: UpdateInfo) {
        val actualSha = sha256(apk)
        require(actualSha.equals(update.sha256, ignoreCase = true)) {
            "Downloaded file failed checksum verification"
        }

        val archive = archivePackageInfo(apk)
            ?: error("Downloaded file is not a valid Android package")

        require(archive.packageName == activity.packageName) {
            "Downloaded package name does not match WIN26"
        }
        require(archive.longVersionCode == update.versionCode) {
            "Downloaded package version does not match the update manifest"
        }
        require(archive.longVersionCode > currentPackageInfo().longVersionCode) {
            "Downloaded package is not newer than the installed app"
        }

        val installedSigner = signerSha256(currentPackageInfo())
        val archiveSigner = signerSha256(archive)
        require(installedSigner != null && archiveSigner != null && installedSigner == archiveSigner) {
            "Downloaded APK is not signed by the WIN26 signing key"
        }
    }

    @Suppress("DEPRECATION")
    private fun currentPackageInfo(): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.packageManager.getPackageInfo(
                activity.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            activity.packageManager.getPackageInfo(
                activity.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(apk: File): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            activity.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        }
    }

    private fun signerSha256(info: PackageInfo): String? {
        val signer = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(signer.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun launchInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }

    private fun showError(prefix: String, error: Exception) {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        showActionDialog(
            title = "Windows Update",
            message = "$prefix.\n\n$detail",
            positiveText = "OK"
        )
    }

    /**
     * Updater-owned action dialog.
     *
     * WIN26's launcher theme can suppress Android AlertDialog's standard button bar.
     * These buttons live inside our own content view, so Download & Install / Cancel
     * remain visible and tappable regardless of the active Windows theme.
     */
    private fun showActionDialog(
        title: String,
        message: String,
        positiveText: String,
        onPositive: (() -> Unit)? = null,
        negativeText: String? = null
    ) {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()

        val dialog = Dialog(activity)
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.rgb(64, 64, 64))
                cornerRadius = dp(4).toFloat()
            }
        }

        val titleView = TextView(activity).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(0, 0, 0, dp(14))
        }
        panel.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val messageView = TextView(activity).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 17f
            setLineSpacing(0f, 1.08f)
            setPadding(0, 0, 0, dp(20))
        }
        panel.addView(
            messageView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        fun makeButton(label: String): Button = Button(activity).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.BLACK)
            minHeight = dp(48)
            minWidth = dp(96)
            background = GradientDrawable().apply {
                setColor(Color.rgb(224, 224, 224))
                cornerRadius = dp(3).toFloat()
                setStroke(dp(1), Color.rgb(96, 96, 96))
            }
        }

        negativeText?.let { label ->
            val negative = makeButton(label)
            negative.setOnClickListener { dialog.dismiss() }
            buttons.addView(
                negative,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(48)
                ).apply { marginEnd = dp(10) }
            )
        }

        val positive = makeButton(positiveText)
        positive.setOnClickListener {
            dialog.dismiss()
            onPositive?.invoke()
        }
        buttons.addView(
            positive,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48)
            )
        )

        panel.addView(
            buttons,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        dialog.setContentView(panel)
        dialog.setCancelable(true)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.55f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }
}
