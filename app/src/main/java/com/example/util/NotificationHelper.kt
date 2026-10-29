package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object NotificationHelper {

    private const val CHANNEL_ID = "cash_flow_safety_channel"
    private const val CHANNEL_NAME = "Cash Flow Safety Alerts"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Notifies when projected cash flow dips below minimum safety threshold"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showLowCashAlertNotification(
        context: Context,
        threshold: Double,
        projectedAmount: Double,
        breachDate: LocalDate,
        isWorstCase: Boolean,
        daysAhead: Long = 0L
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dateStr = breachDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        val scenario = if (isWorstCase) "worst-case" else "average"
        val timeAheadText = if (daysAhead <= 0L) "today" else if (daysAhead == 1L) "tomorrow" else "in $daysAhead days"
        val title = "⚠️ Cash Flow Warning: Low Balance Alert"
        val message = String.format(
            Locale.US,
            "Balance projected to drop to $%,.0f on %s (%s, %s scenario), below your $%,.0f safety threshold.",
            projectedAmount,
            dateStr,
            timeAheadText,
            scenario,
            threshold
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }
}
