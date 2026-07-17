package com.signglove

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** 获取设备当前位置；Google 融合定位不可用时自动回退到 Android 系统定位。 */
object LocationHelper {

    private const val GOOGLE_TIMEOUT_MS = 6_000L
    private const val SYSTEM_TIMEOUT_MS = 10_000L
    private const val FRESH_LOCATION_MS = 2 * 60_000L
    private const val MAX_CACHE_AGE_MS = 30 * 60_000L
    private const val SYSTEM_FUSED_PROVIDER = "fused"

    data class Loc(
        val lat: Double,
        val lon: Double,
        val accuracyMeters: Float?,
        val source: String,
        val capturedAt: Long,
        val cached: Boolean
    )

    data class CurrentResult(val location: Loc?, val error: String? = null)

    /**
     * 先尝试 Google 融合定位；6 秒内无结果则切换到系统网络/GPS/融合定位。
     * 系统定位 10 秒内无结果时，使用不超过 30 分钟的最新缓存位置。
     */
    @SuppressLint("MissingPermission")
    fun current(ctx: Context, cb: (CurrentResult) -> Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            cb(CurrentResult(null, "未授予定位权限"))
            return
        }

        val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            cb(CurrentResult(null, "系统定位服务不可用"))
            return
        }
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            cb(CurrentResult(null, "系统位置信息未开启"))
            return
        }

        val googleAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS
        if (googleAvailable) tryGoogleLocation(ctx, locationManager, cb)
        else trySystemLocation(ctx, locationManager, cb)
    }

    @SuppressLint("MissingPermission")
    private fun tryGoogleLocation(
        ctx: Context,
        locationManager: LocationManager,
        cb: (CurrentResult) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        val stageFinished = AtomicBoolean(false)
        val cts = CancellationTokenSource()

        fun fallback() {
            if (!stageFinished.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(stageFinished)
            cts.cancel()
            trySystemLocation(ctx, locationManager, cb)
        }

        val timeout = Runnable { fallback() }
        handler.postAtTime(timeout, stageFinished, android.os.SystemClock.uptimeMillis() + GOOGLE_TIMEOUT_MS)

        try {
            val client = LocationServices.getFusedLocationProviderClient(ctx)
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        fallback()
                    } else if (stageFinished.compareAndSet(false, true)) {
                        handler.removeCallbacksAndMessages(stageFinished)
                        cb(CurrentResult(toLoc(location, "Google 融合定位")))
                    }
                }
                .addOnFailureListener { fallback() }
        } catch (_: Exception) {
            fallback()
        }
    }

    @SuppressLint("MissingPermission")
    private fun trySystemLocation(
        ctx: Context,
        locationManager: LocationManager,
        cb: (CurrentResult) -> Unit
    ) {
        val allProviders = try {
            locationManager.allProviders.toSet()
        } catch (_: Exception) {
            emptySet()
        }
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            SYSTEM_FUSED_PROVIDER
        ).distinct().filter { provider ->
            provider in allProviders && try {
                locationManager.isProviderEnabled(provider)
            } catch (_: Exception) {
                false
            }
        }

        if (providers.isEmpty()) {
            cb(cachedOrFailure(locationManager, allProviders, "设备没有已启用的定位提供程序"))
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean(false)
        val remaining = AtomicInteger(providers.size)
        val cancellationSignals = mutableListOf<CancellationSignal>()

        fun complete(result: CurrentResult) {
            if (!completed.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(completed)
            cancellationSignals.forEach { it.cancel() }
            cb(result)
        }

        val timeout = Runnable {
            complete(cachedOrFailure(locationManager, allProviders, "系统定位超时，请开启 WLAN/蓝牙扫描后重试"))
        }
        handler.postAtTime(timeout, completed, android.os.SystemClock.uptimeMillis() + SYSTEM_TIMEOUT_MS)

        providers.forEach { provider ->
            val signal = CancellationSignal()
            cancellationSignals.add(signal)
            try {
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(ctx)
                ) { location ->
                    if (location != null) {
                        complete(CurrentResult(toLoc(location, providerName(provider))))
                    } else if (remaining.decrementAndGet() == 0) {
                        complete(cachedOrFailure(locationManager, allProviders, "系统定位未返回位置"))
                    }
                }
            } catch (_: Exception) {
                if (remaining.decrementAndGet() == 0) {
                    complete(cachedOrFailure(locationManager, allProviders, "系统定位调用失败"))
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun cachedOrFailure(
        locationManager: LocationManager,
        providers: Set<String>,
        failure: String
    ): CurrentResult {
        val cached = providers.mapNotNull { provider ->
            try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }
        }.maxByOrNull { it.time }

        if (cached == null) return CurrentResult(null, failure)
        val age = (System.currentTimeMillis() - cached.time).coerceAtLeast(0L)
        if (age > MAX_CACHE_AGE_MS) {
            return CurrentResult(null, "$failure，最近缓存位置已超过 30 分钟")
        }
        return CurrentResult(toLoc(cached, "${providerName(cached.provider)}缓存"))
    }

    private fun toLoc(location: Location, source: String): Loc {
        val capturedAt = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        val age = (System.currentTimeMillis() - capturedAt).coerceAtLeast(0L)
        return Loc(
            lat = location.latitude,
            lon = location.longitude,
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
            source = source,
            capturedAt = capturedAt,
            cached = source.endsWith("缓存") || age > FRESH_LOCATION_MS
        )
    }

    private fun providerName(provider: String?): String = when (provider) {
        LocationManager.GPS_PROVIDER -> "GPS"
        LocationManager.NETWORK_PROVIDER -> "系统网络定位"
        LocationManager.PASSIVE_PROVIDER -> "系统被动定位"
        SYSTEM_FUSED_PROVIDER -> "系统融合定位"
        else -> provider?.let { "系统定位($it)" } ?: "系统定位"
    }

    /** 高德 uri marker 链接（经度,纬度顺序），微信中可直接打开。 */
    fun amapLink(loc: Loc): String =
        "https://uri.amap.com/marker?position=${loc.lon},${loc.lat}&name=求助位置&src=signglove&coordinate=gaode"
}
