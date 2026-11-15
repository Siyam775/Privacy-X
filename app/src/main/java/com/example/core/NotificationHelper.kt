package com.example.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_MESSAGES = "privacyx_messages"
    const val CHANNEL_CALLS = "privacyx_calls"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages & Social",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time chat, story replies, and platform announcements"
            }
            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Voice & Video Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming WebRTC voice and video call alerts"
            }
            manager.createNotificationChannels(listOf(messagesChannel, callsChannel))
        }
    }

    fun showLocalNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        channelId: String = CHANNEL_MESSAGES,
        hidePreview: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayBody = if (hidePreview) "New encrypted activity on PrivacyX" else body
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(displayBody)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }
}
