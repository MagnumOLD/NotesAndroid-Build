package com.magnumold.notes

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo

internal const val TASK_TIMER_CHANNEL = "task_timer"
internal const val TASK_TIMER_NOTIFICATION_ID = 7311
internal const val ACTION_TASK_TIMER_START = "com.magnumold.notes.prototype.TIMER_START"
internal const val ACTION_TASK_TIMER_STOP = "com.magnumold.notes.prototype.TIMER_STOP"
internal const val ACTION_TASK_TIMER_EXPIRED = "com.magnumold.notes.prototype.TIMER_EXPIRED"

class TaskTimerService : Service() {
    private lateinit var store: TaskTimerStore
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            val active = store.activeTimer()
            if (active == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            updateOngoingNotification(active)
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = TaskTimerStore(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TASK_TIMER_STOP -> {
                store.finishActive(System.currentTimeMillis())
                cancelAlarm()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TASK_TIMER_EXPIRED -> handleExpiry()
            ACTION_TASK_TIMER_START -> {
                val taskId = intent.getLongExtra("task_id", 0L)
                val limit = intent.getIntExtra("limit_minutes", store.preferences().defaultLimitMinutes)
                if (taskId != 0L) store.start(taskId, System.currentTimeMillis(), limit)
            }
        }
        val active = store.activeTimer()
        if (active == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, TASK_TIMER_NOTIFICATION_ID, buildNotification(active),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        scheduleAlarm(active.breakUntilMillis ?: active.deadlineMillis)
        handler.removeCallbacks(refresh)
        handler.post(refresh)
        return START_STICKY
    }

    private fun handleExpiry() {
        val active = store.activeTimer() ?: return
        val now = System.currentTimeMillis()
        val settings = store.preferences()
        if (active.breakUntilMillis != null) {
            if (now >= active.breakUntilMillis) {
                store.clearActive()
                showAlert("Перерыв завершён", "Можно вернуться к задаче")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return
        }
        if (now < active.deadlineMillis || active.alerted) return
        when (settings.endMode) {
            TimerEndMode.SIGNAL_ONLY -> {
                store.markAlerted()
                showAlert("Время вышло", "Таймер продолжает работать")
            }
            TimerEndMode.STOP -> {
                store.finishActive(now)
                showAlert("Время вышло", "Рабочая сессия остановлена")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            TimerEndMode.BREAK -> {
                store.finishActiveForBreak(now, now + settings.breakMinutes * 60_000L)
                showAlert("Время вышло", "Начался перерыв на ${settings.breakMinutes} мин")
                scheduleAlarm(now + settings.breakMinutes * 60_000L)
            }
        }
    }

    private fun scheduleAlarm(atMillis: Long) {
        val manager = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, TaskTimerAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            this, 7312, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    private fun cancelAlarm() {
        val pending = PendingIntent.getBroadcast(
            this, 7312, Intent(this, TaskTimerAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        getSystemService(AlarmManager::class.java).cancel(pending)
    }

    private fun buildNotification(active: ActiveTaskTimer): Notification {
        val now = System.currentTimeMillis()
        val remaining = active.remainingMillis(now)
        val title = if (active.isOnBreak(now)) "Перерыв" else "Идёт работа над задачей"
        val stopIntent = PendingIntent.getService(
            this, 7313, Intent(this, TaskTimerService::class.java).setAction(ACTION_TASK_TIMER_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, TASK_TIMER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Осталось: ${formatDuration(remaining)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .addAction(0, "Остановить", stopIntent)
            .build()
    }

    private fun updateOngoingNotification(active: ActiveTaskTimer) {
        getSystemService(NotificationManager::class.java)
            .notify(TASK_TIMER_NOTIFICATION_ID, buildNotification(active))
        val now = System.currentTimeMillis()
        val due = active.breakUntilMillis ?: active.deadlineMillis
        if (now >= due && !active.alerted) handleExpiry()
    }

    private fun showAlert(title: String, body: String) {
        val settings = store.preferences()
        val builder = NotificationCompat.Builder(this, TASK_TIMER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (settings.soundEnabled) builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
        getSystemService(NotificationManager::class.java).notify(TASK_TIMER_NOTIFICATION_ID + 1, builder.build())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(TASK_TIMER_CHANNEL, "Таймер задач", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1_000L).coerceAtLeast(0L)
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

class TaskTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val serviceIntent = Intent(context, TaskTimerService::class.java).setAction(ACTION_TASK_TIMER_EXPIRED)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(serviceIntent)
        else context.startService(serviceIntent)
    }
}

class TaskTimerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (TaskTimerStore(context).activeTimer() == null) return
        val serviceIntent = Intent(context, TaskTimerService::class.java)
        if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, serviceIntent)
        else context.startService(serviceIntent)
    }
}
