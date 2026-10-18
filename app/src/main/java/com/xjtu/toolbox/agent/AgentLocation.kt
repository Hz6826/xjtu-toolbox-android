package com.xjtu.toolbox.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 屁岱 `my_location` 的定位：按需申请权限、定位一次。用系统 [LocationManager]，不依赖 Play 服务；
 * 坐标是 WGS-84，与打包的 OpenStreetMap 校区数据同一套，不用纠偏。
 */
object AgentLocation {

    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var pending: CompletableDeferred<Boolean>? = null

    /** 在 Activity.onCreate 里登记权限弹窗（必须在 STARTED 之前）。 */
    fun register(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pending?.complete(result.values.any { it })
            pending = null
        }
    }

    private fun granted(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private suspend fun ensurePermission(context: Context): Boolean {
        if (granted(context)) return true
        val l = launcher ?: return false
        val wait = CompletableDeferred<Boolean>().also { pending = it }
        withContext(Dispatchers.Main) {
            l.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        return wait.await()
    }

    /** 当前位置 (纬度, 经度)；没授权、定位关了、15 秒内没定到返回 null。两分钟内的上次位置直接用。 */
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun current(context: Context): Pair<Double, Double>? {
        if (!ensurePermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val now = System.currentTimeMillis()
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time < 2 * 60_000 }
            .minByOrNull { it.accuracy }
            ?.let { return it.latitude to it.longitude }
        val provider = providers.firstOrNull() ?: return null
        val fix = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
            }
        }
        return fix?.let { it.latitude to it.longitude }
    }
}
