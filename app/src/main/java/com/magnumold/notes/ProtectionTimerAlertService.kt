package com.magnumold.notes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder

internal class ProtectionTimerAlertService : Service() {
    private var ringtone: Ringtone? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ProtectionTimerContract.ACTION_ALARM -> handleAlarm()
            ProtectionTimerContract.ACTION_STOP_TRACKING -> handleStopTracking(intent)
            ProtectionTimerContract.ACTION_CONTINUE_WORK -> handleContinueWork(intent)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleAlarm() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(null, null))
        val repository = timeRepository()
        val timer = repository.load().protectionTimer
        if (timer == null) {
            finishService()
            return
        }
        val now = System.currentTimeMillis()
        if (now < timer.endsAtMillis) {
            ProtectionTimerScheduler(this).schedule(timer)
            finishService()
            return
        }

        if (timer.endMode == ProtectionEndMode.STOP_AND_PAUSE) {
            repository.update { state ->
                TimeTrackingEngine.finish(
                    state, timer.taskId, now, WorkSessionEndReason.AUTOMATIC_PAUSE
                ).copy(protectionTimer = null)
            }
            notesRepository().updateTaskStatus(timer.taskId, RecordStatus.PAUSED)
            sendTaskStatusChanged()
        } else {
            repository.update { it.copy(protectionTimer = null) }
        }

        if (timer.soundEnabled) playAlarmSound(timer.soundUri)
        startForeground(NOTIFICATION_ID, buildNotification(timer, actionPendingIntent(timer)))
    }

    private fun handleStopTracking(intent: Intent) {
        val taskId = intent.getLongExtra(ProtectionTimerContract.EXTRA_TASK_ID, -1L)
        if (taskId > 0L) {
            timeRepository().update { state ->
                TimeTrackingEngine.finish(state, taskId, System.currentTimeMillis(), WorkSessionEndReason.STOP)
                    .copy(protectionTimer = null)
            }
        }
        stopAlertAndService()
    }

    private fun handleContinueWork(intent: Intent) {
        val taskId = intent.getLongExtra(ProtectionTimerContract.EXTRA_TASK_ID, -1L)
        if (taskId > 0L) {
            val parentId = if (intent.hasExtra(ProtectionTimerContract.EXTRA_PARENT_TASK_ID)) {
                intent.getLongExtra(ProtectionTimerContract.EXTRA_PARENT_TASK_ID, -1L).takeIf { it > 0L }
            } else null
            val taskTitle = intent.getStringExtra(ProtectionTimerContract.EXTRA_TASK_TITLE) ?: "Задача"
            val subtaskTitle = intent.getStringExtra(ProtectionTimerContract.EXTRA_SUBTASK_TITLE)
            if (notesRepository().updateTaskStatus(taskId, RecordStatus.IN_PROGRESS)) {
                timeRepository().update { state ->
                    val clean = state.copy(protectionTimer = null)
                    if (taskId in clean.activeSessions) clean
                    else TimeTrackingEngine.start(
                        state = clean,
                        taskId = taskId,
                        parentTaskId = parentId,
                        taskTitle = taskTitle,
                        subtaskTitle = subtaskTitle,
                        nowMillis = System.currentTimeMillis()
                    )
                }
                sendTaskStatusChanged()
            }
        }
        stopAlertAndService()
    }

    private fun buildNotification(
        timer: ProtectionTimer?,
        actionIntent: PendingIntent?
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (timer == null) "Таймер защиты" else "Время вышло")
            .setContentText(timer?.subtaskTitle ?: timer?.taskTitle ?: "Обработка сигнала")
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setShowWhen(false)

        if (timer != null && actionIntent != null) {
            val label = if (timer.endMode == ProtectionEndMode.SIGNAL_ONLY) {
                "Остановить учёт"
            } else {
                "Продолжить работу"
            }
            builder.addAction(android.R.drawable.ic_media_pause, label, actionIntent)
        }
        return builder.build()
    }

    private fun actionPendingIntent(timer: ProtectionTimer): PendingIntent {
        val action = if (timer.endMode == ProtectionEndMode.SIGNAL_ONLY) {
            ProtectionTimerContract.ACTION_STOP_TRACKING
        } else {
            ProtectionTimerContract.ACTION_CONTINUE_WORK
        }
        val intent = Intent(this, ProtectionTimerAlertService::class.java)
            .setAction(action)
            .putExtra(ProtectionTimerContract.EXTRA_TASK_ID, timer.taskId)
            .putExtra(ProtectionTimerContract.EXTRA_TASK_TITLE, timer.taskTitle)
            .putExtra(ProtectionTimerContract.EXTRA_SUBTASK_TITLE, timer.subtaskTitle)
            .putExtra(ProtectionTimerContract.EXTRA_END_MODE, timer.endMode.name)
        timer.parentTaskId?.let {
            intent.putExtra(ProtectionTimerContract.EXTRA_PARENT_TASK_ID, it)
        }
        val requestCode = timer.taskId.hashCode() + if (action == ProtectionTimerContract.ACTION_STOP_TRACKING) 10 else 20
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun playAlarmSound(uriString: String?) {
        stopRingtone()
        val uri = uriString?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone?.isLooping = true
        ringtone?.play()
    }

    private fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
    }

    private fun timeRepository() = TimeTrackingRepository(
        getSharedPreferences("time_tracking", MODE_PRIVATE)
    )

    private fun notesRepository() = NotesRepository(
        getSharedPreferences("MainActivity_preferences", MODE_PRIVATE)
    )

    private fun sendTaskStatusChanged() {
        sendBroadcast(
            Intent(ProtectionTimerContract.ACTION_TASK_STATUS_CHANGED).setPackage(packageName)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Таймер защиты",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Сигнал об окончании таймера защиты"
                        setSound(null, null)
                        enableVibration(true)
                    }
                )
            }
        }
    }

    private fun stopAlertAndService() {
        stopRingtone()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        finishService()
    }

    private fun finishService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopRingtone()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "protection_timer_alerts"
        const val NOTIFICATION_ID = 7432
    }
}
