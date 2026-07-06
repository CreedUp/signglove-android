package com.signglove

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.signglove.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.random.Random

private data class DemoScriptItem(val words: String, val sentence: String)

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var settings: Settings
    private lateinit var bt: BluetoothBle
    private lateinit var composer: SentenceComposer
    private lateinit var vitals: Vitals
    private lateinit var sos: Sos
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsWarned = false

    private var deviceMacs = listOf<String>()
    private var connected = false
    private val flow = StringBuilder()
    private val history = StringBuilder()
    private val main = Handler(Looper.getMainLooper())
    private var sosDialog: AlertDialog? = null
    private var demoScriptEnabled = false
    private val demoScriptRuns = mutableListOf<Runnable>()

    // 手势模拟: 发一组(2~4)词 → 停顿(>pauseSec)触发组句 → 再发下一组
    private var simRunning = false
    private var simWordsLeft = 0
    private val simNames = listOf("fist", "open", "point", "victory", "ok")
    private val simTick = object : Runnable {
        override fun run() {
            if (!simRunning) return
            handleGestureName(simNames[Random.nextInt(simNames.size)])
            simWordsLeft--
            if (simWordsLeft > 0) {
                main.postDelayed(this, (600 + Random.nextInt(400)).toLong())  // 词间 0.6~1.0s
            } else {
                simWordsLeft = 2 + Random.nextInt(3)                           // 下一组 2~4 词
                main.postDelayed(this, (settings.pauseSec * 1000).toLong() + 1200) // 停顿>阈值, 触发组句
            }
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { refreshDevices() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        settings = Settings(this)
        b.tvTitle.text = "🧤 手语手套 · 智能监测  v1.4"
        initTts()

        composer = SentenceComposer(settings,
            onWord = { w -> flow.append(if (flow.isEmpty()) "" else " ").append(w)
                b.tvFlow.text = "手势词: $flow"; b.tvStatus.text = "… 组句中" },
            onComposing = { flow.clear(); b.tvFlow.text = ""; b.tvStatus.text = "… 组句中" },
            onSentence = { text, src -> onSentence(text, src) })

        vitals = Vitals(
            onUpdate = { v -> showVitals(v) },
            onDanger = { reason, v -> if (settings.autoSos) sos.trigger(reason, v) })

        sos = Sos(this, settings,
            onCountdown = { sec, reason -> updateCountdown(sec, reason) },
            onPushed = { ok, detail -> onPushed(ok, detail) })

        bt = BluetoothBle(
            ctx = this,
            onLine = { line -> GestureMap.parseGesture(line)?.let { name -> handleGestureName(name) } },
            onState = { c -> connected = c
                b.tvBle.text = if (c) "蓝牙: 已连接" else "蓝牙: 未连接"
                b.btnConnect.text = if (c) "⏏ 断开" else "🔌 连接"
                updateVitalsConnectionState() })

        wireControls()
        updateVitalsConnectionState()
        requestPerms()
        vitals.startSim()   // 生命体征模拟(同 PC)
    }

    private fun wireControls() {
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.tvDemoScript.text = settings.demoButtonText

        b.btnConnect.setOnClickListener {
            if (connected) { bt.disconnect() }
            else {
                val pos = b.spDevices.selectedItemPosition
                if (pos < 0 || pos >= deviceMacs.size) { toast("请先在系统设置配对 JDY-18, 再刷新选择"); return@setOnClickListener }
                if (!hasBt()) { requestPerms(); return@setOnClickListener }
                bt.connect(deviceMacs[pos]); toast("连接中…")
            }
        }

        b.swSim.setOnCheckedChangeListener { _, on ->
            simRunning = on
            if (on) { showWaitingGesture(); simWordsLeft = 2 + Random.nextInt(3); main.post(simTick) }
            else main.removeCallbacks(simTick)
        }
        b.tvDemoScript.setOnClickListener {
            clearDemoScript()
            if (demoScriptItems().isEmpty()) {
                toast("请先在设置里填写演示脚本")
                return@setOnClickListener
            }
            demoScriptEnabled = true
            showWaitingGesture()
            startDemoScript()
        }
        b.swAutoSos.isChecked = settings.autoSos
        b.swAutoSos.setOnCheckedChangeListener { _, on -> settings.autoSos = on
            toast("自动报警 " + if (on) "开" else "关") }

        b.btnSosTest.setOnClickListener {
            if (!settings.autoSos) { toast("请先打开自动报警开关"); return@setOnClickListener }
            toast("注入异常生命体征…"); vitals.injectDanger()
        }
        b.btnDemo.setOnClickListener {
            val demo = listOf(
                DemoScriptItem("你好", "你好"),
                DemoScriptItem("谢谢", "谢谢"),
                DemoScriptItem("请问 需要 帮助 吗", "请问需要帮助吗"),
                DemoScriptItem("我 肚子 饿了 想 吃饭", "我肚子饿了想吃饭")
            )
            runSingleDemoItem(demo[Random.nextInt(demo.size)])
        }
    }

    private fun handleGestureName(name: String) {
        if (demoScriptEnabled) return
        GestureMap.word(name)?.let { composer.feed(it) }
    }

    private fun demoScriptItems(): List<DemoScriptItem> =
        settings.demoText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val parts = line.split("=>", limit = 2)
                if (parts.size == 2) {
                    DemoScriptItem(parts[0].trim(), parts[1].trim())
                } else {
                    DemoScriptItem(line, line)
                }
            }
            .filter { it.words.isNotEmpty() && it.sentence.isNotEmpty() }

    private fun startDemoScript() {
        val items = demoScriptItems()
        if (items.isEmpty()) return
        clearDemoScript()
        var delayMs = (settings.demoFirstDelaySec * 1000).toLong().coerceAtLeast(0L)
        val intervalMs = (settings.demoIntervalSec * 1000).toLong().coerceAtLeast(0L)
        val intervalOverridesMs = demoIntervalOverridesMs()
        val wordIntervalMs = (settings.demoWordIntervalSec * 1000).toLong().coerceAtLeast(0L)
        val wordIntervalOverridesMs = demoWordIntervalOverridesMs()
        val composeDelayMs = (settings.demoComposeDelaySec * 1000).toLong().coerceAtLeast(0L)
        items.forEachIndexed { index, item ->
            val r = Runnable {
                if (!demoScriptEnabled) return@Runnable
                showDemoItem(item, index == items.lastIndex)
            }
            demoScriptRuns.add(r)
            main.postDelayed(r, delayMs)
            delayMs += demoItemDurationMs(item, wordIntervalMs, wordIntervalOverridesMs, composeDelayMs)
            if (index < items.lastIndex) {
                delayMs += intervalOverridesMs.getOrNull(index) ?: intervalMs
            }
        }
    }

    private fun demoIntervalOverridesMs(): List<Long> =
        parseDelayOverridesMs(settings.demoIntervalsText)

    private fun demoWordIntervalOverridesMs(): List<Long> =
        parseDelayOverridesMs(settings.demoWordIntervalsText)

    private fun parseDelayOverridesMs(text: String): List<Long> =
        text
            .split(Regex("[,，;；\\s]+"))
            .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() }?.toFloatOrNull() }
            .map { (it * 1000).toLong().coerceAtLeast(0L) }

    private fun clearDemoScript() {
        demoScriptRuns.forEach { main.removeCallbacks(it) }
        demoScriptRuns.clear()
    }

    private fun showWaitingGesture() {
        flow.clear()
        b.tvGesture.text = "等待手势…"
        b.tvStatus.text = ""
        b.tvFlow.text = ""
    }

    private fun runSingleDemoItem(item: DemoScriptItem) {
        demoScriptEnabled = true
        clearDemoScript()
        showWaitingGesture()
        val r = Runnable {
            if (!demoScriptEnabled) return@Runnable
            showDemoItem(item, isLast = true)
        }
        demoScriptRuns.add(r)
        main.postDelayed(r, 500L)
    }

    private fun showDemoItem(item: DemoScriptItem, isLast: Boolean) {
        flow.clear()
        val words = item.words.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val wordIntervalMs = (settings.demoWordIntervalSec * 1000).toLong().coerceAtLeast(0L)
        val wordIntervalOverridesMs = demoWordIntervalOverridesMs()
        val composeDelayMs = (settings.demoComposeDelaySec * 1000).toLong().coerceAtLeast(0L)
        var delayMs = 0L

        words.forEachIndexed { index, word ->
            val r = Runnable {
                if (!demoScriptEnabled) return@Runnable
                flow.append(if (flow.isEmpty()) "" else " ").append(word)
                b.tvFlow.text = "手势词: $flow"
                b.tvStatus.text = "… 组句中"
            }
            demoScriptRuns.add(r)
            main.postDelayed(r, delayMs)
            if (index < words.lastIndex) {
                delayMs += wordIntervalOverridesMs.getOrNull(index) ?: wordIntervalMs
            }
        }

        val sentenceRun = Runnable {
            if (!demoScriptEnabled) return@Runnable
            onSentence(item.sentence, "script")
            if (isLast) {
                demoScriptEnabled = false
            }
        }
        demoScriptRuns.add(sentenceRun)
        main.postDelayed(sentenceRun, delayMs + composeDelayMs)
    }

    private fun demoItemDurationMs(
        item: DemoScriptItem,
        wordIntervalMs: Long,
        wordIntervalOverridesMs: List<Long>,
        composeDelayMs: Long
    ): Long {
        val wordCount = item.words.split(Regex("\\s+")).count { it.trim().isNotEmpty() }
        val lastWordAt = (0 until (wordCount - 1)).sumOf { index ->
            wordIntervalOverridesMs.getOrNull(index) ?: wordIntervalMs
        }
        return lastWordAt + composeDelayMs
    }

    private fun onSentence(text: String, src: String) {
        b.tvGesture.text = text      // 句子持久展示在大字区
        b.tvStatus.text = ""         // 清"组句中"状态
        b.tvFlow.text = ""
        flow.clear()
        speak(text)
        val tag = when (src) {
            "deepseek" -> "[☁DeepSeek]"
            "local" -> "[直拼·未配Key]"
            "fallback" -> "[回退·DeepSeek失败]"
            "demo" -> "[演示]"
            "script" -> "[演示]"
            else -> ""
        }
        history.insert(0, "• $text  $tag\n")
        b.tvHistory.text = history.toString()
    }

    private fun showVitals(v: VitalsData) {
        if (!connected) {
            showVitalsPlaceholder()
            return
        }
        b.tvHr.text = "❤️\n${v.hr}\nBPM"
        b.tvSpo2.text = "🩸\n${v.spo2}\n%"
        b.tvTemp.text = "🌡️\n${v.temp}\n℃"
    }

    private fun updateVitalsConnectionState() {
        if (!connected) showVitalsPlaceholder()
    }

    private fun showVitalsPlaceholder() {
        b.tvHr.text = "❤️\n--\nBPM"
        b.tvSpo2.text = "🩸\n--\n%"
        b.tvTemp.text = "🌡️\n--\n℃"
    }

    private fun updateCountdown(sec: Int, reason: String) {
        if (sec <= 0) { sosDialog?.dismiss(); sosDialog = null; return }
        if (sosDialog == null) {
            sosDialog = AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("🆘 检测到生命体征异常")
                .setPositiveButton("立即求助") { _, _ -> sos.fireNow() }
                .setNegativeButton("我没事，取消") { _, _ -> sos.cancel() }
                .create()
            sosDialog?.show()
        }
        sosDialog?.setMessage("$reason\n\n$sec 秒后自动向家人发送求助与定位")
    }

    private fun onPushed(ok: Boolean, detail: String) {
        toast(if (ok) "✓ 已向家人发送求助" else "推送失败: $detail")
        history.insert(0, (if (ok) "🆘 已向家人发送求助\n" else "🆘 求助未送达: $detail\n"))
        b.tvHistory.text = history.toString()
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            main.post {
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    ttsReady = false
                    toastOnceForTts("语音播报初始化失败，请检查手机 TTS 引擎")
                    return@post
                }

                val zh = engine.setLanguage(Locale.CHINA)
                ttsReady = zh != TextToSpeech.LANG_MISSING_DATA && zh != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsReady) {
                    val fallback = engine.setLanguage(Locale.getDefault())
                    ttsReady = fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED
                    if (!ttsReady) {
                        toastOnceForTts("手机未安装可用语音包，请在系统设置安装文字转语音引擎")
                    } else {
                        toastOnceForTts("未找到中文语音包，已使用系统默认语音")
                    }
                }
            }
        }
    }

    private fun speak(text: String) {
        val engine = tts
        if (engine == null) {
            toastOnceForTts("语音播报尚未初始化")
            return
        }
        if (!ttsReady) {
            main.postDelayed({ if (ttsReady) speak(text) else toastOnceForTts("语音播报不可用，请检查手机 TTS 设置") }, 500L)
            return
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "signglove-tts")
        if (result == TextToSpeech.ERROR) toastOnceForTts("语音播报失败，请检查媒体音量和 TTS 引擎")
    }

    private fun toastOnceForTts(message: String) {
        if (ttsWarned) return
        ttsWarned = true
        toast(message)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---- 权限 / 设备 ----
    private fun hasBt(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun requestPerms() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            need.add(Manifest.permission.BLUETOOTH_CONNECT)
            need.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        need.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        val ask = need.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (ask.isNotEmpty()) permLauncher.launch(ask.toTypedArray()) else refreshDevices()
    }

    private fun refreshDevices() {
        if (!bt.isAvailable()) { b.tvBle.text = "蓝牙: 设备不支持"; return }
        if (!bt.isEnabled()) { b.tvBle.text = "蓝牙: 未开启(请打开手机蓝牙)" }
        val list = bt.bondedDevices()
        deviceMacs = list.map { it.second }
        val labels = if (list.isEmpty()) listOf("无已配对设备(先配对 JDY-18)")
                     else list.map { "${it.first}  ${it.second}" }
        b.spDevices.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    }

    override fun onResume() {
        super.onResume()
        b.tvDemoScript.text = settings.demoButtonText
        if (hasBt()) refreshDevices()
    }

    override fun onDestroy() {
        bt.disconnect(); vitals.stopSim(); simRunning = false; clearDemoScript()
        tts?.shutdown()
        super.onDestroy()
    }
}
