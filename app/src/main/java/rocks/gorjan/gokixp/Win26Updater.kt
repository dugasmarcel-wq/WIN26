package rocks.gorjan.gokixp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
                    if (update.versionCode <= currentCode) {
                        AlertDialog.Builder(activity)
                            .setTitle("Windows Update")
                            .setMessage("WIN26 is up to date.\n\nInstalled: \${current.versionName ?: currentCode}")
                            .setPositiveButton("OK", null)
                            .show()
                    } else {
                        AlertDialog.Builder(activity)
                            .setTitle("Windows Update")
                            .setMessage(
                                "A WIN26 update is available.\n\n" +
                                    "Installed: \${current.versionName ?: currentCode}\n" +
                                    "Available: \${update.versionName}"
                            )
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Download & Install") { _, _ ->
                                beginInstall(update)
                            }
                            .show()
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
            AlertDialog.Builder(activity)
                .setTitle("Allow WIN26 updates")
                .setMessage(
                    "Android needs a one-time permission so WIN26 can install its own signed updates. " +
                        "Turn on “Allow from this source”, return to WIN26, then press Windows Update again."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:\${activity.packageName}")
                    )
                    activity.startActivity(intent)
                }
                .show()
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
                "Update server returned HTTP \${connection.responseCode}"
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
                "APK server returned HTTP \${connection.responseCode}"
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
            "\${activity.packageName}.fileprovider",
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
        AlertDialog.Builder(activity)
            .setTitle("Windows Update")
            .setMessage("$prefix.\n\n$detail")
            .setPositiveButton("OK", null)
            .show()
    }
}
