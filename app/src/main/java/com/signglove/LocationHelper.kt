package com.signglove

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** 取手机 GPS 定位, 生成高德地图链接(免 key)。 */
object LocationHelper {

    data class Loc(val lat: Double, val lon: Double)

    /** 异步取当前位置, 回调 (可能为 null)。需已授予定位权限。 */
    @SuppressLint("MissingPermission")
    fun current(ctx: Context, cb: (Loc?) -> Unit) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(ctx)
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) cb(Loc(loc.latitude, loc.longitude))
                    else client.lastLocation
                        .addOnSuccessListener { l -> cb(l?.let { Loc(it.latitude, it.longitude) }) }
                        .addOnFailureListener { cb(null) }
                }
                .addOnFailureListener { cb(null) }
        } catch (e: Exception) {
            cb(null)
        }
    }

    /** 高德 uri marker 链接(经度,纬度 顺序), 微信里可点开看位置。 */
    fun amapLink(loc: Loc): String =
        "https://uri.amap.com/marker?position=${loc.lon},${loc.lat}&name=求助位置&src=signglove&coordinate=gaode"
}
