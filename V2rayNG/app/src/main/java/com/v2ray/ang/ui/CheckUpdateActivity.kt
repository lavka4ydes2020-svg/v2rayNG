package com.v2ray.ang.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.databinding.ActivityCheckUpdateBinding
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.launch
import java.io.File

class CheckUpdateActivity : BaseActivity() {

    private val binding by lazy { ActivityCheckUpdateBinding.inflate(layoutInflater) }

    private var pendingDownloadId: Long = -1L
    private var pendingVersion: String? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == pendingDownloadId && id != -1L) {
                installApk()
                pendingDownloadId = -1L
                pendingVersion = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(
            binding.root,
            showHomeAsUp = true,
            title = getString(R.string.update_check_for_update)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }

        binding.layoutCheckUpdate.setOnClickListener {
            checkForUpdates(binding.checkPreRelease.isChecked)
        }

        binding.checkPreRelease.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, isChecked)
        }
        binding.checkPreRelease.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false)

        binding.switchAutoCheckUpdate.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_AUTO_CHECK_UPDATE, isChecked)
        }
        binding.switchAutoCheckUpdate.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_CHECK_UPDATE, true)

        "v${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})".also {
            binding.tvVersion.text = it
        }

        checkForUpdates(binding.checkPreRelease.isChecked)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadReceiver)
    }

    private fun checkForUpdates(includePreRelease: Boolean) {
        toast(R.string.update_checking_for_update)
        showLoading()

        lifecycleScope.launch {
            try {
                val result = UpdateCheckerManager.checkForUpdate(includePreRelease)
                if (result.hasUpdate) {
                    showUpdateDialog(result)
                } else {
                    toastSuccess(R.string.update_already_latest_version)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to check for updates: ${e.message}")
                toastError(e.message ?: getString(R.string.toast_failure))
            } finally {
                hideLoading()
            }
        }
    }

    private fun showUpdateDialog(result: CheckUpdateResult) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_new_version_found, result.latestVersion))
            .setMessage(result.releaseNotes)
            .setPositiveButton(R.string.update_now) { _, _ ->
                result.downloadUrl?.let { url ->
                    downloadAndInstall(url, result.latestVersion ?: "")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadAndInstall(downloadUrl: String, version: String) {
        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(downloadUrl)

            val fileName = "AlfredoVPN_${version}.apk"

            val request = DownloadManager.Request(uri)
                .setTitle("Alfredo VPN $version")
                .setDescription(getString(R.string.update_downloading))
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    this,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                .setMimeType("application/vnd.android.package-archive")

            pendingDownloadId = dm.enqueue(request)
            pendingVersion = version

            toast(R.string.update_download_started)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to start download: ${e.message}")
            toastError(e.message ?: getString(R.string.toast_failure))
        }
    }

    private fun installApk() {
        try {
            val version = pendingVersion ?: return
            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "AlfredoVPN_${version}.apk")
            if (!file.exists()) {
                LogUtil.e(AppConfig.TAG, "Downloaded APK not found: ${file.absolutePath}")
                toastError(getString(R.string.toast_failure))
                return
            }

            val fileProviderUri = FileProvider.getUriForFile(
                this,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileProviderUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to install APK: ${e.message}")
            toastError(e.message ?: getString(R.string.toast_failure))
        }
    }
}
