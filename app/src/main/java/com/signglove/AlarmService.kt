package com.signglove

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 前台服务: 持续响铃(STREAM_ALARM 最大音量)+ 持续震动, 直到 stopService。
 * 配 fullScreenIntent 通知, 锁屏也能拉起 AlarmActivity。满足"一直响到手动关闭"。
 */
class AlarmService : Service() {

    companion object {
        const val CHANNEL = "sos_alarm"
        const val NOTIF_ID = 911
        const val EXTRA_REASON = "reason"
        @Volatile var running = false
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "生命体征异常"
        running = true
        startForeground(NOTIF_ID, buildNotification(reason))
        acquireWake()
        startAlarm()
        // 拉起全屏告警界面
        startActivity(Intent(this, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_REASON, reason))
        return START_STICKY
    }

    private fun buildNotification(reason: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "紧急求助", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "生命体征异常告警"
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
        val full = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_REASON, reason),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("🆘 紧急求助")
            .setContentText("检测到$reason · 点击查看并停止")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .build()
    }

    private fun startAlarm() {
        // 音量调到最大
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (_: Exception) {}
        // 循环铃声
        try {
            var uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            if (uri != null) {
                player = MediaPlayer().apply {
                    setDataSource(this@AlarmService, uri)
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                    isLooping = true
                    prepare()
                    start()
                }
            }
        } catch (_: Exception) {}
        // 循环震动
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 600, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun acquireWake() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "signglove:sos").apply { acquire(10 * 60 * 1000L) }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        running = false
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
