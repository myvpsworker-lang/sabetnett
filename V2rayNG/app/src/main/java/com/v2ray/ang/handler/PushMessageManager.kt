package com.v2ray.ang.handler

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.TimeUnit

/**
 * Admin messages delivered as system notifications without Google services: a periodic background job
 * downloads the public config and shows the admin's push message once per message id. Delivery takes up
 * to the polling interval; urgent text belongs in the emergency dialog.
 */
object PushMessageManager {
    private const val WORK_NAME = "sabetnet_push_poll"
    private const val POLL_MINUTES = 30L
    private const val CHANNEL_ID = "admin_messages_channel"
    private const val NOTIFICATION_ID = 31

    /** A message is shown when one is published and its id differs from the last one shown. */
    fun shouldShow(push: RemotePush?, seenId: Int): Boolean = push != null && push.id != seenId

    /** Schedules the periodic poll; an existing schedule is kept so repeated calls are harmless. */
    fun schedule(context: Context = AngApplication.application) {
        try {
            val request = PeriodicWorkRequestBuilder<PollTask>(POLL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            RemoteWorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Push poll: scheduling failed", e)
        }
    }

    /** Downloads the config and shows the pending push message, if any. Never throws. */
    suspend fun pollOnce(context: Context) {
        RemoteConfigManager.load()
        if (!RemoteConfigManager.refresh()) return
        val push = RemoteConfigManager.config.value.push
        if (!shouldShow(push, RemoteConfigManager.dismissed.value.pushSeenId) || push == null) return
        if (show(context, push)) RemoteConfigManager.markPushSeen(push.id)
    }

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled()
    private fun show(context: Context, push: RemotePush): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        NotificationHelper.ensureNotificationChannel(
            context = context,
            channelId = CHANNEL_ID,
            channelNameRes = R.string.notification_channel_admin_messages,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = push.url
            ?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: Intent(context, MainActivity::class.java)
        val title = push.title.ifEmpty { context.getString(R.string.app_name) }
        val accent = RemoteConfigManager.config.value.theme.accentArgb
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(push.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(push.text))
            .setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_ID, open, flags))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (accent != null) builder.setColor(accent)
        return try {
            manager.notify(NOTIFICATION_ID, builder.build())
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Push poll: showing message failed, id=${push.id}", e)
            false
        }
    }

    class PollTask(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            try {
                pollOnce(applicationContext)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Push poll: run failed", e)
            }
            return Result.success()
        }
    }
}
