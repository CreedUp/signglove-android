package com.signglove

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.InputStream
import java.util.UUID
import kotlin.concurrent.thread

/**
 * 经典蓝牙 SPP (RFCOMM) 连接。先在系统设置里配对 JDY-31(PIN 1234),
 * 本类列出已配对设备、连接、按行读取手势数据。
 */
@SuppressLint("MissingPermission")
class BluetoothSpp(
    private val onLine: (String) -> Unit,
    private val onState: (Boolean) -> Unit   // true=已连接
) {
    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    @Volatile private var running = false

    fun isAvailable(): Boolean = adapter != null
    fun isEnabled(): Boolean = adapter?.isEnabled == true

    /** 已配对设备列表 (名称, MAC)。 */
    fun bondedDevices(): List<Pair<String, String>> =
        adapter?.bondedDevices?.map { (it.name ?: "未知") to it.address } ?: emptyList()

    /** 连接指定 MAC 的设备, 起读线程; 断线自动重连。 */
    fun connect(mac: String) {
        disconnect()
        val dev: BluetoothDevice = adapter?.getRemoteDevice(mac) ?: return
        running = true
        thread {
            while (running) {
                try {
                    val s = dev.createRfcommSocketToServiceRecord(SPP_UUID)
                    adapter?.cancelDiscovery()
                    s.connect()
                    socket = s
                    main.post { onState(true) }
                    readLoop(s.inputStream)
                } catch (e: Exception) {
                    main.post { onState(false) }
                    if (!running) break
                    Thread.sleep(3000)   // 3s 后重连
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null
                }
            }
        }
    }

    private fun readLoop(input: InputStream) {
        val buf = ByteArray(256)
        val sb = StringBuilder()
        while (running) {
            val n = try { input.read(buf) } catch (e: Exception) { break }
            if (n < 0) break
            sb.append(String(buf, 0, n, Charsets.UTF_8))
            var idx = sb.indexOf("\n")
            while (idx >= 0) {
                val line = sb.substring(0, idx)
                sb.delete(0, idx + 1)
                val l = line.trim()
                if (l.isNotEmpty()) main.post { onLine(l) }
                idx = sb.indexOf("\n")
            }
        }
        main.post { onState(false) }
    }

    fun disconnect() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
