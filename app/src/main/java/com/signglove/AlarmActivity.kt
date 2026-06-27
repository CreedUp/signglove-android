package com.signglove

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** 全屏告警界面: 锁屏也弹出, 大红屏 + 唯一"停止"按钮(手动点才停)。 */
class AlarmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 锁屏显示 + 点亮屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        setContentView(R.layout.activity_alarm)

        val reason = intent?.getStringExtra(AlarmService.EXTRA_REASON) ?: "生命体征异常"
        findViewById<TextView>(R.id.alarm_reason).text = reason

        findViewById<Button>(R.id.alarm_stop).setOnClickListener {
            stopService(Intent(this, AlarmService::class.java))
            finish()
        }
    }

    // 屏蔽返回键, 必须点"停止"
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* no-op: 必须手动停止 */ }
}
