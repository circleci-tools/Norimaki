package com.unhappychoice.norimaki.infrastructure.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build as AndroidBuild
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.notificationText
import com.unhappychoice.norimaki.domain.model.notificationTitle
import com.unhappychoice.norimaki.domain.model.statusColor
import com.unhappychoice.norimaki.domain.model.uniqueId

class BuildNotifier(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun notify(build: Build) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel()
        manager.notify(build.uniqueId().hashCode(), notification(build))
    }

    private fun notification(build: Build) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(build.statusColor())
            .setContentTitle(build.notificationTitle())
            .setContentText(build.notificationText())
            .setStyle(NotificationCompat.BigTextStyle().bigText(build.notificationText()))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannel() {
        if (AndroidBuild.VERSION.SDK_INT < AndroidBuild.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Build results", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "build_results"
    }
}
