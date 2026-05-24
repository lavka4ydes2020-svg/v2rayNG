package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.ui.PerAppProxyActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val NOTIFICATION_PENDING_INTENT_PER_APP = 3
    private const val NOTIFICATION_ICON_THRESHOLD = 3000
    private const val QUERY_INTERVAL_MS = 3000L

    /** Alfredo VPN purple accent color. */
    private val ALFREDO_PURPLE = Color.parseColor("#CE93D8")

    private var lastQueryTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: NotificationManager? = null

    /** Elapsed realtime when the VPN connection started (for uptime display). */
    private var connectionStartElapsed = 0L

    /** Previous tick's total TX bytes (system-wide) for traffic delta calculation. */
    private var lastTotalTx = 0L

    /** Previous tick's total RX bytes (system-wide) for traffic delta calculation. */
    private var lastTotalRx = 0L

    /**
     * Call this when the VPN connection is established to start the uptime counter.
     */
    fun markConnectionStarted() {
        connectionStartElapsed = SystemClock.elapsedRealtime()
        // Reset traffic baseline for the new connection
        lastTotalTx = 0L
        lastTotalRx = 0L
    }

    /**
     * Starts the notification updater that shows live traffic, uptime, and proxy status.
     * Runs always (no longer gated by PREF_SPEED_ENABLED).
     */
    fun startSpeedNotification() {
        if (speedNotificationJob != null || CoreServiceManager.isRunning() == false) return

        var lastZeroSpeed = false

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                lastZeroSpeed = updateSpeedNotificationOnce(lastZeroSpeed)
                delay(QUERY_INTERVAL_MS)
            }
        }
    }

    /**
     * Creates and shows the initial VPN foreground notification.
     */
    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return

        // Reset last query time and mark connection start
        lastQueryTime = System.currentTimeMillis()
        markConnectionStarted()

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags
        )

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(
            service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags
        )

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        restartV2RayIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(
            service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags
        )

        // Intent to open Per-App Proxy settings directly
        val perAppIntent = Intent(service, PerAppProxyActivity::class.java)
        perAppIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val perAppPendingIntent = PendingIntent.getActivity(
            service, NOTIFICATION_PENDING_INTENT_PER_APP, perAppIntent, flags
        )

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(service)
        } else {
            ""
        }

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(currentConfig?.remarks ?: service.getString(R.string.app_name))
            .setContentText(buildContentText(service))
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildBigText(service)))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setColor(ALFREDO_PURPLE)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_description_24dp,
                service.getString(R.string.notification_action_proxy_apps),
                perAppPendingIntent
            )
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.notification_action_stop_v2ray),
                stopV2RayPendingIntent
            )
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.title_service_restart),
                restartV2RayPendingIntent
            )

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    /**
     * Cancels the notification and stops the updater.
     */
    fun cancelNotification() {
        val service = getService() ?: return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)

        mBuilder = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
        connectionStartElapsed = 0L
    }

    /**
     * Stops the notification updater without removing the notification.
     */
    fun stopSpeedNotification() {
        speedNotificationJob?.let {
            it.cancel()
            speedNotificationJob = null
            updateNotification("", 0, 0)
        }
    }

    // ====== Channel setup ======

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(context: Context): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val channelName = AppConfig.RAY_NG_CHANNEL_NAME
        val existingChannel = getNotificationManager()?.getNotificationChannel(channelId)
        if (existingChannel != null) return channelId

        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        chan.importance = NotificationManager.IMPORTANCE_LOW
        chan.lightColor = ALFREDO_PURPLE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        chan.setShowBadge(false)
        getNotificationManager()?.createNotificationChannel(chan)
        return channelId
    }

    // ====== Content builders ======

    /**
     * Builds the condensed one-line content text for the notification.
     */
    private fun buildContentText(context: Context): String {
        val appsInfo = getPerAppInfo(context)
        return context.getString(R.string.notification_content_connected, appsInfo)
    }

    /**
     * Builds the expanded BigText content with full details.
     */
    private fun buildBigText(context: Context): String {
        val sb = StringBuilder()

        // Uptime
        sb.append(context.getString(R.string.notification_uptime, getUptimeString()))
        sb.append("\n")

        // Per-app info
        sb.append(getPerAppInfo(context))
        sb.append("\n")

        // Routing info
        sb.append(getRoutingInfo(context))
        sb.append("\n")

        // Traffic headers (filled in by the updater)
        sb.append(context.getString(R.string.notification_traffic_loading))

        return sb.toString()
    }

    /**
     * Returns a formatted uptime string since the connection started.
     */
    private fun getUptimeString(): String {
        if (connectionStartElapsed == 0L) return "0s"
        val elapsedMs = SystemClock.elapsedRealtime() - connectionStartElapsed
        val totalSec = elapsedMs / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Returns per-app proxy status description.
     */
    private fun getPerAppInfo(context: Context): String {
        val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
        val count = apps?.size ?: 0
        return when {
            count == 0 -> context.getString(R.string.notification_per_app_no_apps)
            else -> context.getString(R.string.notification_per_app_count, count)
        }
    }

    /**
     * Returns routing mode description.
     */
    private fun getRoutingInfo(context: Context): String {
        val rulesets = MmkvManager.decodeRoutingRulesets()
        if (rulesets.isNullOrEmpty()) return ""
        val hasRussiaBypass = rulesets.any { rule ->
            rule.enabled && rule.domain?.contains("category-ru") == true
        }
        val hasChinaBypass = rulesets.any { rule ->
            rule.enabled && rule.domain?.contains("geosite:cn") == true
        }
        return when {
            hasRussiaBypass -> context.getString(R.string.notification_routing_russia)
            hasChinaBypass -> context.getString(R.string.notification_routing_china)
            else -> context.getString(R.string.notification_routing_global)
        }
    }

    /**
     * Formats a single speed line for the notification.
     */
    private fun formatSpeedLine(tag: String, up: Double, down: Double): String {
        val label = tag.take(min(tag.length, 6))
        return "$label  ↑ ${up.toLong().toSpeedString()}  ↓ ${down.toLong().toSpeedString()}"
    }

    // ====== Notification updater ======

    /**
     * Builds the BigText for a live update, replacing the placeholder traffic line.
     */
    private fun buildBigTextForUpdate(context: Context, contentText: String?): String {
        val sb = StringBuilder()

        // Uptime
        sb.append(context.getString(R.string.notification_uptime, getUptimeString()))
        sb.append("\n")

        // Per-app info
        sb.append(getPerAppInfo(context))
        sb.append("\n")

        // Routing info
        sb.append(getRoutingInfo(context))

        // Traffic data
        if (!contentText.isNullOrBlank()) {
            sb.append("\n")
            sb.append(contentText)
        }

        return sb.toString()
    }

    /**
     * Queries traffic stats and updates the notification with live data.
     * Always updates full content every tick (uptime, per-app, routing, traffic).
     */
    private fun updateSpeedNotificationOnce(lastZeroSpeed: Boolean): Boolean {
        val queryTime = System.currentTimeMillis()
        val sinceLastQueryIn = (queryTime - lastQueryTime)

        if (sinceLastQueryIn < QUERY_INTERVAL_MS) {
            LogUtil.w(AppConfig.TAG, "Query interval too short: ${sinceLastQueryIn}ms, skipping")
            lastQueryTime = queryTime
            return lastZeroSpeed
        }
        val sinceLastQueryInSeconds = sinceLastQueryIn / 1000.0

        // Use Android TrafficStats to measure total bytes since boot.
        // When VPN is active, all device traffic routes through the TUN interface,
        // so system-wide bytes ≈ VPN traffic.
        val currentTx = TrafficStats.getTotalTxBytes()
        val currentRx = TrafficStats.getTotalRxBytes()

        // On the first tick we only record the baseline.
        if (lastTotalTx == 0L && lastTotalRx == 0L) {
            lastTotalTx = currentTx
            lastTotalRx = currentRx
            lastQueryTime = queryTime
            return true  // first tick = "zero speed"
        }

        val txDelta = currentTx - lastTotalTx
        val rxDelta = currentRx - lastTotalRx

        lastTotalTx = currentTx
        lastTotalRx = currentRx

        Log.i(AppConfig.TAG, "NTF txDelta=$txDelta rxDelta=$rxDelta")

        // Build traffic lines — both counted as proxy traffic since we can't
        // easily split proxy vs direct at the system level.
        val speedText = StringBuilder()
        speedText.append(formatSpeedLine(
            AppConfig.TAG_PROXY,
            txDelta / sinceLastQueryInSeconds,
            rxDelta / sinceLastQueryInSeconds,
        ))
        speedText.append("\n")
        speedText.append(formatSpeedLine(
            AppConfig.TAG_DIRECT,
            0.0,
            0.0,
        ))
        updateNotification(speedText.toString(), txDelta + rxDelta, 0L)

        lastQueryTime = queryTime
        return txDelta + rxDelta == 0L
    }

    /**
     * Updates the notification's big text, content text, and icon with the latest data.
     */
    private fun updateNotification(contentText: String?, proxyTraffic: Long, directTraffic: Long) {
        if (mBuilder != null) {
            if (proxyTraffic < NOTIFICATION_ICON_THRESHOLD && directTraffic < NOTIFICATION_ICON_THRESHOLD) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_name)
            } else if (proxyTraffic > directTraffic) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_proxy)
            } else {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_direct)
            }

            val context = getService() ?: return
            val bigText = buildBigTextForUpdate(context, contentText)
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            mBuilder?.setContentText(
                context.getString(R.string.notification_content_connected, getPerAppInfo(context))
            )
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    // ====== Helpers ======

    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.get()?.getService()
    }
}
