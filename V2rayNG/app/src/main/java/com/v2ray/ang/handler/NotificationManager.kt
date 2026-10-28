package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val NOTIFICATION_ICON_THRESHOLD = 3000
    private const val QUERY_INTERVAL_MS = 3000L
    private const val ACCENT_COLOR = 0xFF4F5BD5.toInt()

    private var lastQueryTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: NotificationManager? = null

    // Content shown by the custom notification layouts; kept so every refresh rebuilds identical views.
    private var currentTitle: String = ""
    private var stopPendingIntent: PendingIntent? = null
    private var restartPendingIntent: PendingIntent? = null
    private var lastSpeed: SpeedSnapshot? = null

    // Identity of the last posted update; an identical refresh is skipped so the system UI is not
    // asked to re-inflate the custom layouts for nothing.
    private var lastPostedKey: String? = null

    /** Per-second traffic (bytes) for the proxy and direct outbounds. */
    private data class SpeedSnapshot(
        val proxyUp: Long,
        val proxyDown: Long,
        val directUp: Long,
        val directDown: Long
    )

    /**
     * Starts the speed notification.
     * @param currentConfig The current profile configuration.
     */
    fun startSpeedNotification() {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) return
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
     * Shows the notification.
     * @param currentConfig The current profile configuration.
     */
    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return

        // Reset last query time to avoid querying stats too soon after showing the notification
        lastQueryTime = System.currentTimeMillis()

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        restartV2RayIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags)

        stopPendingIntent = stopV2RayPendingIntent
        restartPendingIntent = restartV2RayPendingIntent
        currentTitle = currentConfig?.remarks ?: service.getString(R.string.app_name)
        lastSpeed = null
        lastPostedKey = null

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setColor(ACCENT_COLOR)
            .setContentTitle(currentTitle)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(buildContentViews(service, expanded = false))
            .setCustomBigContentView(buildContentViews(service, expanded = true))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)

        //mBuilder?.setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    /**
     * Fulfills or refreshes the foreground-service contract before a start command can
     * return early. A duplicate startForegroundService call still requires the service
     * to enter foreground state promptly, even when the core is already running.
     */
    fun ensureForeground() {
        val service = getService() ?: return
        val notification = mBuilder?.build()
        if (notification == null) showNotification(null) else service.startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Cancels the notification.
     */
    fun cancelNotification() {
        val service = getService() ?: return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)

        mBuilder = null
        lastPostedKey = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
    }

    /**
     * Stops the speed notification.
     */
    fun stopSpeedNotification() {
        speedNotificationJob?.let {
            it.cancel()
            speedNotificationJob = null
            updateNotification(null, 0, 0)
        }
    }

    /**
     * Creates a notification channel for Android O and above.
     * @return The channel ID.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val service = getService() ?: return channelId
        NotificationHelper.ensureNotificationChannel(
            context = service,
            channelId = channelId,
            channelNameRes = R.string.notification_channel_service,
            // Foreground-service notifications must remain visible; LOW is silent but valid.
            importance = NotificationManager.IMPORTANCE_LOW,
        ) {
            lightColor = Color.DKGRAY
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        return channelId
    }

    /**
     * Refreshes the notification with the latest traffic figures.
     * @param speed The per-second traffic, or null to show no traffic (speed display disabled/stopped).
     * @param proxyTraffic The proxy traffic.
     * @param directTraffic The direct traffic.
     */
    private fun updateNotification(speed: SpeedSnapshot?, proxyTraffic: Long, directTraffic: Long) {
        val builder = mBuilder ?: return
        val service = getService() ?: return
        val iconRes = if (proxyTraffic < NOTIFICATION_ICON_THRESHOLD && directTraffic < NOTIFICATION_ICON_THRESHOLD) {
            R.drawable.ic_stat_name
        } else if (proxyTraffic > directTraffic) {
            R.drawable.ic_stat_proxy
        } else {
            R.drawable.ic_stat_direct
        }
        val key = "$iconRes|$speed"
        if (key == lastPostedKey) return
        lastPostedKey = key
        builder.setSmallIcon(iconRes)
        lastSpeed = speed
        builder.setCustomContentView(buildContentViews(service, expanded = false))
        builder.setCustomBigContentView(buildContentViews(service, expanded = true))
        getNotificationManager()?.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Builds the collapsed or expanded notification content.
     * Only plain TextView/ImageView/LinearLayout views are used so the layouts stay valid for RemoteViews.
     */
    private fun buildContentViews(service: Service, expanded: Boolean): RemoteViews {
        val views = RemoteViews(
            service.packageName,
            if (expanded) R.layout.notification_expanded else R.layout.notification_collapsed
        )
        val speed = lastSpeed
        views.setTextViewText(R.id.notif_title, currentTitle)
        views.setTextViewText(R.id.notif_stop, service.getString(R.string.notification_action_stop_v2ray))
        stopPendingIntent?.let { views.setOnClickPendingIntent(R.id.notif_stop, it) }

        if (expanded) {
            views.setTextViewText(R.id.notif_app, service.getString(R.string.app_name))
            views.setTextViewText(R.id.notif_restart, service.getString(R.string.title_service_restart))
            restartPendingIntent?.let { views.setOnClickPendingIntent(R.id.notif_restart, it) }
            views.setTextViewText(R.id.notif_proxy_label, AppConfig.TAG_PROXY.uppercase())
            views.setTextViewText(R.id.notif_direct_label, AppConfig.TAG_DIRECT.uppercase())
            if (speed == null) {
                views.setViewVisibility(R.id.notif_stats, View.GONE)
            } else {
                views.setViewVisibility(R.id.notif_stats, View.VISIBLE)
                views.setTextViewText(R.id.notif_proxy_up, "↑ ${speed.proxyUp.toSpeedString()}")
                views.setTextViewText(R.id.notif_proxy_down, "↓ ${speed.proxyDown.toSpeedString()}")
                views.setTextViewText(R.id.notif_direct_up, "↑ ${speed.directUp.toSpeedString()}")
                views.setTextViewText(R.id.notif_direct_down, "↓ ${speed.directDown.toSpeedString()}")
            }
        } else {
            val line = if (speed == null) {
                service.getString(R.string.app_name)
            } else {
                "↑ ${speed.proxyUp.toSpeedString()}   ↓ ${speed.proxyDown.toSpeedString()}"
            }
            views.setTextViewText(R.id.notif_speed, line)
        }
        return views
    }

    /**
     * Gets the notification manager.
     * @return The notification manager.
     */
    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    /**
     * Updates the speed notification once.
     * Queries traffic stats, separates proxy and direct, and updates the notification.
     * @param lastZeroSpeed The previous zero speed state.
     * @return The current zero speed state.
     */
    private fun updateSpeedNotificationOnce(lastZeroSpeed: Boolean): Boolean {
        val queryTime = System.currentTimeMillis()
        val sinceLastQueryIn = (queryTime - lastQueryTime)

        // If the query interval is too short, skip this round to avoid excessive CPU usage
        if (sinceLastQueryIn < QUERY_INTERVAL_MS) {
            LogUtil.w(AppConfig.TAG, "Query interval too short: ${sinceLastQueryIn}ms, skipping")
            lastQueryTime = queryTime
            return lastZeroSpeed
        }
        val sinceLastQueryInSeconds = sinceLastQueryIn / 1000.0

        var proxyUplink = 0L
        var proxyDownlink = 0L
        var directUplink = 0L
        var directDownlink = 0L

        CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
            when {
                stat.tag == AppConfig.TAG_DIRECT -> {
                    when (stat.direction) {
                        AppConfig.UPLINK -> directUplink += stat.value
                        AppConfig.DOWNLINK -> directDownlink += stat.value
                    }
                }

                // Accumulate stats for all proxy outbounds (including custom subscription tags)
                stat.tag != AppConfig.TAG_BLOCKED -> {
                    when (stat.direction) {
                        AppConfig.UPLINK -> proxyUplink += stat.value
                        AppConfig.DOWNLINK -> proxyDownlink += stat.value
                    }
                }
            }
        }

        val proxyTotal = proxyUplink + proxyDownlink
        val directTotal = directUplink + directDownlink
        val zeroSpeed = proxyTotal + directTotal == 0L
        if (!zeroSpeed || !lastZeroSpeed) {
            val snapshot = SpeedSnapshot(
                proxyUp = (proxyUplink / sinceLastQueryInSeconds).toLong(),
                proxyDown = (proxyDownlink / sinceLastQueryInSeconds).toLong(),
                directUp = (directUplink / sinceLastQueryInSeconds).toLong(),
                directDown = (directDownlink / sinceLastQueryInSeconds).toLong()
            )
            updateNotification(snapshot, proxyTotal, directTotal)
        }
        lastQueryTime = queryTime
        return zeroSpeed
    }

    /**
     * Gets the service instance.
     * @return The service instance.
     */
    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.get()?.getService()
    }
}
