package com.example.languagereminder.statusbar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.languagereminder.MainActivity
import com.example.languagereminder.R
import com.example.languagereminder.data.WeekdayTextStore
import com.example.languagereminder.util.DayOfWeekResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class StatusBarService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var store: WeekdayTextStore
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var latestValues: Map<DayOfWeek, String> = DayOfWeek.entries.associateWith { "" }

    override fun onCreate() {
        super.onCreate()
        store = WeekdayTextStore(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        startForeground(NOTIFICATION_ID, createNotification(""))
        observeTexts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeTexts() {
        serviceScope.launch {
            store.weekdayTexts.collectLatest { values ->
                latestValues = values
                updateNotification()
            }
        }
        serviceScope.launch {
            while (isActive) {
                updateNotification()
                delay(60_000L)
            }
        }
    }

    private fun updateNotification() {
        val today = DayOfWeekResolver.today()
        val text = latestValues[today] ?: WeekdayTextStore.defaultText(today)
        val notification = createNotification(text)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.status_bar_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.status_bar_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "status_bar_channel"
        private const val NOTIFICATION_ID = 9002

        fun start(context: Context) {
            val intent = Intent(context, StatusBarService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StatusBarService::class.java))
        }
    }
}
