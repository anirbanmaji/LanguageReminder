package com.example.languagereminder.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
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

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var widgetView: FloatingWidgetView
    private lateinit var store: WeekdayTextStore
    private var layoutParams: WindowManager.LayoutParams? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var latestValues: Map<DayOfWeek, String> = DayOfWeek.entries.associateWith { "" }

    override fun onCreate() {
        super.onCreate()
        store = WeekdayTextStore(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification())
        createOverlay()
        observeTexts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        layoutParams?.let {
            kotlin.runCatching { windowManager.removeView(widgetView.rootView) }
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 250
        }
        layoutParams = params

        widgetView = FloatingWidgetView(context = this)
        widgetView.installDragBehavior(params, windowManager)
        windowManager.addView(widgetView.rootView, params)
    }

    private fun observeTexts() {
        serviceScope.launch {
            store.weekdayTexts.collectLatest { values ->
                latestValues = values
                refreshToday()
            }
        }
        serviceScope.launch {
            while (isActive) {
                refreshToday()
                delay(60_000L)
            }
        }
    }

    private fun refreshToday() {
        widgetView.bindValues(latestValues, DayOfWeekResolver.today())
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_channel_description)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_channel_name))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 9001

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
