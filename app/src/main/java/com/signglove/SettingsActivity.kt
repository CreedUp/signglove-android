package com.signglove

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.signglove.databinding.ActivitySettingsBinding

/** 设置界面: 读写 SharedPreferences (Server酱/企微/DeepSeek/称呼/停顿)。 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private lateinit var settings: Settings
    private var blankTapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = getString(R.string.settings_title)

        settings = Settings(this)
        // 填入现值
        b.etServerchan.setText(settings.serverchan)
        b.etWebhook.setText(settings.webhook)
        b.etName.setText(settings.userName)
        b.etDsKey.setText(settings.deepseekKey)
        b.etPause.setText(settings.pauseSec.toString())
        b.groupDemoSettings.visibility = View.GONE
        b.etDemoButtonText.setText(settings.demoButtonText)
        b.etDemoFirstDelay.setText(settings.demoFirstDelaySec.toString())
        b.etDemoInterval.setText(settings.demoIntervalSec.toString())
        b.etDemoWordInterval.setText(settings.demoWordIntervalSec.toString())
        b.etDemoWordIntervals.setText(settings.demoWordIntervalsText)
        b.etDemoComposeDelay.setText(settings.demoComposeDelaySec.toString())
        b.etDemoText.setText(settings.demoText)

        b.settingsRoot.setOnClickListener { onBlankSettingsTap() }
        b.root.setOnClickListener { onBlankSettingsTap() }

        b.btnSave.setOnClickListener {
            settings.serverchan = b.etServerchan.text.toString().trim()
            settings.webhook = b.etWebhook.text.toString().trim()
            settings.userName = b.etName.text.toString().trim().ifBlank { "手语手套用户" }
            settings.deepseekKey = b.etDsKey.text.toString().trim()
            settings.pauseSec = b.etPause.text.toString().trim().toFloatOrNull() ?: 2.5f
            settings.demoButtonText = b.etDemoButtonText.text.toString().trim().ifBlank { "演示输出脚本" }
            settings.demoFirstDelaySec = (b.etDemoFirstDelay.text.toString().trim().toFloatOrNull() ?: 1.0f).coerceAtLeast(0f)
            settings.demoIntervalSec = (b.etDemoInterval.text.toString().trim().toFloatOrNull() ?: 2.0f).coerceAtLeast(0f)
            settings.demoWordIntervalSec = (b.etDemoWordInterval.text.toString().trim().toFloatOrNull() ?: 0.6f).coerceAtLeast(0f)
            settings.demoWordIntervalsText = b.etDemoWordIntervals.text.toString().trim()
            settings.demoComposeDelaySec = (b.etDemoComposeDelay.text.toString().trim().toFloatOrNull() ?: 0.8f).coerceAtLeast(0f)
            settings.demoText = b.etDemoText.text.toString().trim()
            Toast.makeText(this, "✓ 设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun onBlankSettingsTap() {
        blankTapCount++
        if (blankTapCount < 5) return
        blankTapCount = 0
        b.groupDemoSettings.visibility =
            if (b.groupDemoSettings.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }
}
