package com.example.languagereminder.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import com.example.languagereminder.MainActivity
import com.example.languagereminder.data.DisplayMode
import com.example.languagereminder.data.WeekdayTextStore
import com.example.languagereminder.util.DayOfWeekResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var widgetView: FloatingWidgetView? = null
    private lateinit var store: WeekdayTextStore
    private var layoutParams: WindowManager.LayoutParams? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var latestValues: Map<DayOfWeek, String> = DayOfWeek.entries.associateWith { "" }
    private var currentMode: DisplayMode = DisplayMode.FLOATING_WIDGET
    private var lastNotifiedDay: DayOfWeek? = null
    private var lastNotifiedText: String? = null
    private var lastNotifiedMode: DisplayMode? = null

    override fun onCreate() {
        super.onCreate()
        store = WeekdayTextStore(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Use a placeholder title for the very first foreground call
        val initialNotification = createNotification("Starting...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
        
        observeData()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay() {
        if (widgetView != null) return

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

        val view = FloatingWidgetView(context = this)
        view.installDragBehavior(params, windowManager)
        windowManager.addView(view.rootView, params)
        widgetView = view
    }

    private fun removeOverlay() {
        widgetView?.let {
            kotlin.runCatching { windowManager.removeView(it.rootView) }
        }
        widgetView = null
        layoutParams = null
    }

    private fun observeData() {
        serviceScope.launch {
            combine(store.weekdayTexts, store.displayMode) { texts, mode ->
                texts to mode
            }.collectLatest { (texts, mode) ->
                latestValues = texts
                currentMode = mode
                refreshUI()
            }
        }
        
        // Check for day changes every minute
        serviceScope.launch {
            while (isActive) {
                delay(60_000L)
                val today = DayOfWeekResolver.today()
                if (today != lastNotifiedDay) {
                    refreshUI()
                }
            }
        }
    }

    private fun refreshUI() {
        val today = DayOfWeekResolver.today()
        val textToShow = latestValues[today] ?: WeekdayTextStore.defaultText(today)

        if (currentMode == DisplayMode.FLOATING_WIDGET) {
            removeOverlay()
            createOverlay()
            widgetView?.bindValues(latestValues, today)
            updateNotificationIfChanged("Language Reminder Active", today)
        } else {
            removeOverlay()
            updateNotificationIfChanged(textToShow, today)
        }
    }

    private fun updateNotificationIfChanged(contentText: String, today: DayOfWeek) {
        if (contentText == lastNotifiedText && today == lastNotifiedDay && currentMode == lastNotifiedMode) {
            return
        }

        lastNotifiedText = contentText
        lastNotifiedDay = today
        lastNotifiedMode = currentMode

        val notification = createNotification(contentText)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(contentText: String): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Language Reminder Service",
                NotificationManager.IMPORTANCE_DEFAULT // Changed from LOW to DEFAULT
            ).apply {
                description = "Shows your daily reminder"
                setShowBadge(false)
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

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(contentText)
            .setContentText("Daily Reminder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "reminder_channel_v5" // Incremented version to ensure fresh settings
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
