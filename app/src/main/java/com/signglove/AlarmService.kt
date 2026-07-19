package com.signglove

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 前台服务: 循环播报“我需要帮助”(STREAM_ALARM 最大音量)+ 持续震动, 直到 stopService。
 * 配 fullScreenIntent 通知, 锁屏也能拉起 AlarmActivity。满足"一直响到手动关闭"。
 */
class AlarmService : Service() {

    companion object {
        const val CHANNEL = "sos_alarm"
        const val NOTIF_ID = 911
        const val EXTRA_REASON = "reason"
        private const val HELP_MESSAGE = "我需要帮助"
        private const val HELP_UTTERANCE_ID = "signglove-sos-help"
        private const val HELP_REPEAT_DELAY_MS = 450L
        @Volatile var running = false
    }

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    @Volatile private var keepSpeaking = false
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
        // 循环中文求助语音；TTS 不可用时才回退到系统告警铃声，确保紧急告警仍有声音。
        if (!keepSpeaking) startHelpVoiceLoop()
        // 循环震动
        startVibration()
    }

    private fun startHelpVoiceLoop() {
        keepSpeaking = true
        try {
            tts = TextToSpeech(applicationContext) { status ->
                val engine = tts
                if (!keepSpeaking || engine == null) return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) {
                    startFallbackTone()
                    return@TextToSpeech
                }

                val zhResult = engine.setLanguage(Locale.CHINA)
                if (zhResult == TextToSpeech.LANG_MISSING_DATA ||
                    zhResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) engine.setLanguage(Locale.getDefault())
                engine.setSpeechRate(0.9f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == HELP_UTTERANCE_ID) scheduleNextHelpMessage()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post { if (keepSpeaking) startFallbackTone() }
                    }
                })
                speakHelpMessage()
            }
        } catch (_: Exception) {
            startFallbackTone()
        }
    }

    private fun speakHelpMessage() {
        if (!keepSpeaking) return
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = tts?.speak(
            HELP_MESSAGE,
            TextToSpeech.QUEUE_FLUSH,
            params,
            HELP_UTTERANCE_ID
        ) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) startFallbackTone()
    }

    private fun scheduleNextHelpMessage() {
        main.postDelayed({ if (keepSpeaking) speakHelpMessage() }, HELP_REPEAT_DELAY_MS)
    }

    private fun startFallbackTone() {
        if (!keepSpeaking || player != null) return
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
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
    }

    private fun startVibration() {
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
        keepSpeaking = false
        main.removeCallbacksAndMessages(null)
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
