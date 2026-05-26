package com.v2ray.ang.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.core.content.ContextCompat
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
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class CheckUpdateActivity : BaseActivity() {

    private val binding by lazy { ActivityCheckUpdateBinding.inflate(layoutInflater) }

    private var pendingDownloadId: Long = -1L
    private var pendingVersion: String? = null
    private var downloadPollingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(
            binding.root,
            showHomeAsUp = true,
            title = getString(R.string.update_check_for_update)
        )

        binding.checkPreRelease.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, isChecked)
            checkForUpdates(isChecked)
        }
        binding.checkPreRelease.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false)

        binding.switchAutoCheckUpdate.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_AUTO_CHECK_UPDATE, isChecked)
        }
        binding.switchAutoCheckUpdate.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_CHECK_UPDATE, true)

        binding.btnDownload.setOnClickListener {
            pendingDownloadUrl?.let { url ->
                downloadAndInstall(url, pendingVersion ?: "")
            }
        }

        checkForUpdates(binding.checkPreRelease.isChecked)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadPollingJob?.cancel()
    }

    private var pendingDownloadUrl: String? = null

    private fun checkForUpdates(includePreRelease: Boolean) {
        showCheckingState()
        lifecycleScope.launch {
            try {
                val result = UpdateCheckerManager.checkForUpdate(includePreRelease)
                if (result.hasUpdate) {
                    showUpdateAvailable(result)
                } else {
                    showUpToDate()
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Update check failed: ${e.message}")
                showUpToDate()
            }
        }
    }

    private fun showCheckingState() {
        with(binding) {
            progressChecking.visibility = View.VISIBLE
            iconStatus.visibility = View.GONE
            tvStatusTitle.text = getString(R.string.update_checking_title)
            tvStatusTitle.setTextColor(ContextCompat.getColor(this@CheckUpdateActivity, R.color.md_theme_secondary))
            tvStatusSubtitle.text = getString(R.string.update_checking_subtitle)
            layoutVersion.visibility = View.GONE
            tvChangelog.visibility = View.GONE
            layoutDownloadProgress.visibility = View.GONE
            btnDownload.visibility = View.GONE
            cardStatus.setCardBackgroundColor(
                ContextCompat.getColor(this@CheckUpdateActivity, R.color.md_theme_secondaryContainer)
            )
        }
    }

    private fun showUpToDate() {
        with(binding) {
            progressChecking.visibility = View.GONE
            iconStatus.visibility = View.VISIBLE
            iconStatus.setImageResource(R.drawable.ic_fab_check)
            iconStatus.imageTintList = ContextCompat.getColorStateList(
                this@CheckUpdateActivity, android.R.color.holo_green_dark
            )
            tvStatusTitle.text = getString(R.string.update_up_to_date)
            tvStatusTitle.setTextColor(
                ContextCompat.getColor(this@CheckUpdateActivity, android.R.color.holo_green_dark)
            )
            tvStatusSubtitle.text = getString(R.string.update_up_to_date_subtitle)
            tvVersionName.text = getString(R.string.update_version_format, BuildConfig.VERSION_NAME)
            tvLibVersion.text = getString(R.string.update_lib_format, CoreNativeManager.getLibVersion())
            layoutVersion.visibility = View.VISIBLE
            tvChangelog.visibility = View.GONE
            layoutDownloadProgress.visibility = View.GONE
            btnDownload.visibility = View.GONE
            cardStatus.setCardBackgroundColor(
                ContextCompat.getColor(this@CheckUpdateActivity, R.color.update_card_green)
            )
        }
    }

    private fun showUpdateAvailable(result: CheckUpdateResult) {
        pendingDownloadUrl = result.downloadUrl
        pendingVersion = result.latestVersion
        with(binding) {
            progressChecking.visibility = View.GONE
            iconStatus.visibility = View.VISIBLE
            iconStatus.setImageResource(R.drawable.ic_check_update_24dp)
            iconStatus.imageTintList = ContextCompat.getColorStateList(
                this@CheckUpdateActivity, R.color.amber_700
            )
            tvStatusTitle.text = getString(
                R.string.update_new_version_title, result.latestVersion ?: ""
            )
            tvStatusTitle.setTextColor(
                ContextCompat.getColor(this@CheckUpdateActivity, R.color.amber_700)
            )
            tvStatusSubtitle.text = getString(R.string.update_recommended)
            layoutVersion.visibility = View.GONE
            if (!result.releaseNotes.isNullOrBlank()) {
                tvChangelog.visibility = View.VISIBLE
                tvChangelog.text = result.releaseNotes
            } else {
                tvChangelog.visibility = View.GONE
            }
            layoutDownloadProgress.visibility = View.GONE
            btnDownload.visibility = View.VISIBLE
            cardStatus.setCardBackgroundColor(
                ContextCompat.getColor(this@CheckUpdateActivity, R.color.update_card_amber)
            )
        }
    }

    private fun showDownloadingState() {
        with(binding) {
            progressChecking.visibility = View.GONE
            iconStatus.visibility = View.VISIBLE
            iconStatus.setImageResource(R.drawable.ic_check_update_24dp)
            iconStatus.imageTintList = ContextCompat.getColorStateList(
                this@CheckUpdateActivity, R.color.amber_700
            )
            tvStatusTitle.text = getString(R.string.update_downloading_title)
            tvStatusTitle.setTextColor(
                ContextCompat.getColor(this@CheckUpdateActivity, R.color.amber_700)
            )
            tvStatusSubtitle.text = getString(R.string.update_downloading_subtitle)
            layoutVersion.visibility = View.GONE
            tvChangelog.visibility = View.GONE
            btnDownload.visibility = View.GONE
            layoutDownloadProgress.visibility = View.VISIBLE
            progressDownload.isIndeterminate = true
            progressDownload.progress = 0
            tvDownloadProgress.text = getString(R.string.update_download_starting)
            cardStatus.setCardBackgroundColor(
                ContextCompat.getColor(this@CheckUpdateActivity, R.color.update_card_amber)
            )
        }
    }

    private fun updateDownloadProgress(percent: Int, bytesDownloaded: Long, totalBytes: Long) {
        with(binding) {
            if (percent < 0) {
                // Size not yet known — indeterminate spinner
                progressDownload.isIndeterminate = true
                val mbDownloaded = bytesDownloaded / (1024.0 * 1024.0)
                tvDownloadProgress.text = getString(
                    R.string.update_download_indeterminate,
                    String.format("%.1f", mbDownloaded)
                )
            } else {
                progressDownload.isIndeterminate = false
                progressDownload.progress = percent
                val mbDownloaded = bytesDownloaded / (1024.0 * 1024.0)
                val mbTotal = totalBytes / (1024.0 * 1024.0)
                tvDownloadProgress.text = getString(
                    R.string.update_download_progress_format,
                    percent,
                    mbDownloaded,
                    mbTotal
                )
            }
        }
    }

    private fun showInstallingState() {
        with(binding) {
            layoutDownloadProgress.visibility = View.GONE
            progressChecking.visibility = View.GONE
            iconStatus.visibility = View.VISIBLE
            iconStatus.setImageResource(R.drawable.ic_fab_check)
            iconStatus.imageTintList = ContextCompat.getColorStateList(
                this@CheckUpdateActivity, R.color.amber_700
            )
            tvStatusTitle.text = getString(R.string.update_installing)
            tvStatusTitle.setTextColor(
                ContextCompat.getColor(this@CheckUpdateActivity, R.color.amber_700)
            )
            tvStatusSubtitle.text = ""
            tvChangelog.visibility = View.GONE
            btnDownload.visibility = View.GONE
        }
    }

    private fun showDownloadError() {
        with(binding) {
            layoutDownloadProgress.visibility = View.GONE
            btnDownload.visibility = View.VISIBLE
            progressChecking.visibility = View.GONE
            iconStatus.visibility = View.GONE
            tvStatusTitle.text = getString(R.string.update_download_failed)
            tvStatusTitle.setTextColor(
                ContextCompat.getColor(this@CheckUpdateActivity, android.R.color.holo_red_dark)
            )
            tvStatusSubtitle.text = getString(R.string.update_tap_retry)
        }
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
                    DownloadManager.Request.VISIBILITY_VISIBLE
                )
                .setDestinationInExternalFilesDir(
                    this,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                .setMimeType("application/vnd.android.package-archive")

            pendingDownloadId = dm.enqueue(request)
            pendingVersion = version

            showDownloadingState()
            startDownloadPolling(dm)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to start download: ${e.message}")
            toastError(e.message ?: getString(R.string.toast_failure))
        }
    }

    private fun startDownloadPolling(dm: DownloadManager) {
        downloadPollingJob?.cancel()
        downloadPollingJob = lifecycleScope.launch {
            while (isActive) {
                delay(300)
                val cursor: Cursor = try {
                    val query = DownloadManager.Query().setFilterById(pendingDownloadId)
                    dm.query(query)
                } catch (e: Exception) {
                    null
                } ?: continue

                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    val bytesDownloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val totalBytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            cursor.close()
                            installApk()
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> {
                            cursor.close()
                            showDownloadError()
                            return@launch
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            if (totalBytes > 0) {
                                val percent = ((bytesDownloaded * 100) / totalBytes).toInt()
                                updateDownloadProgress(percent, bytesDownloaded, totalBytes)
                            } else {
                                // Total size not yet known — show indeterminate
                                updateDownloadProgress(-1, bytesDownloaded, 0)
                            }
                        }
                    }
                }
                cursor.close()
            }
        }
    }

    private fun installApk() {
        showInstallingState()
        try {
            val version = pendingVersion ?: return
            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "AlfredoVPN_${version}.apk")
            if (!file.exists()) {
                LogUtil.e(AppConfig.TAG, "Downloaded APK not found: ${file.absolutePath}")
                toastError(getString(R.string.toast_failure))
                return
            }

            // Check install permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!packageManager.canRequestPackageInstalls()) {
                    toast(R.string.update_allow_install_unknown)
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                    )
                    startActivity(settingsIntent)
                    return
                }
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
