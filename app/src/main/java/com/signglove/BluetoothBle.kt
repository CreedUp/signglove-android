package com.signglove

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

/**
 * BLE GATT 连接。先在系统设置里配对 JDY-18(PIN 000000),
 * 本类列出已配对设备、连接 GATT、订阅 FFE1 通知、按行读取手势数据。
 *
 * 对外契约与原 BluetoothSpp 完全一致(onLine/onState + 同名方法),
 * 仅构造多一个 ctx 参数(connectGatt 需要 Context)。
 */
@SuppressLint("MissingPermission")
class BluetoothBle(
    private val ctx: Context,
    private val onLine: (String) -> Unit,
    private val onState: (Boolean) -> Unit   // true=已连接
) {
    companion object {
        val UUID_FFE0: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb") // JDY-18 透传服务
        val UUID_FFE1: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb") // 通知特征(notify+write)
        val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // 启用通知必须写
    }

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var gatt: BluetoothGatt? = null
    private var mac: String? = null
    @Volatile private var running = false
    private val lineBuf = StringBuilder()   // 跨通知包累积(JDY-18 通知可能分包)

    fun isAvailable(): Boolean = adapter != null
    fun isEnabled(): Boolean = adapter?.isEnabled == true

    /** 已配对设备列表 (名称, MAC)。BLE 连接前一般也要先系统配对, 兜底用。 */
    fun bondedDevices(): List<Pair<String, String>> =
        adapter?.bondedDevices?.map { (it.name ?: "未知") to it.address } ?: emptyList()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                main.post { onState(false) }
                try { g.close() } catch (_: Exception) {}
                gatt = null
                if (running && mac != null) {
                    main.postDelayed({ if (running) reconnect() }, 3000)   // 3s 后重连
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(UUID_FFE0) ?: return
            val ch = svc.getCharacteristic(UUID_FFE1) ?: return
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(UUID_CCCD)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(cccd)
            }
            main.post { onState(true) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value ?: return
            // 复用原 readLoop 的切行逻辑: 累积 → 按 \n 切 → trim → 非空 → onLine
            synchronized(lineBuf) {
                lineBuf.append(String(bytes, Charsets.UTF_8))
                var idx = lineBuf.indexOf("\n")
                while (idx >= 0) {
                    val line = lineBuf.substring(0, idx)
                    lineBuf.delete(0, idx + 1)
                    val l = line.trim()
                    if (l.isNotEmpty()) main.post { onLine(l) }
                    idx = lineBuf.indexOf("\n")
                }
            }
        }
    }

    /** 连接指定 MAC 的设备; 断线在 onConnectionStateChange 里自动重连。 */
    fun connect(mac: String) {
        disconnect()
        this.mac = mac
        val dev: BluetoothDevice = adapter?.getRemoteDevice(mac) ?: return
        running = true
        try {
            gatt = dev.connectGatt(ctx, false, gattCallback)
        } catch (e: Exception) {
            main.post { onState(false) }
            main.postDelayed({ if (running) reconnect() }, 3000)
        }
    }

    private fun reconnect() {
        val m = mac ?: return
        val dev = adapter?.getRemoteDevice(m) ?: return
        try {
            gatt = dev.connectGatt(ctx, false, gattCallback)
        } catch (e: Exception) {
            main.postDelayed({ if (running) reconnect() }, 3000)
        }
    }

    fun disconnect() {
        running = false
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
    }
}
