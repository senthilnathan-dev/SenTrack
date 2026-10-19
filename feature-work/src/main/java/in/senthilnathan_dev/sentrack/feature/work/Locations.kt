package `in`.senthilnathan_dev.sentrack.feature.work

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * One-shot location for punching in or out. It is requested only when the user taps the button, waits at most a few
 * seconds, and returns null when permission is off, location is switched off, or no fix arrives, so punching never
 * fails because of location.
 */
internal object Locations {
    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPermission(context: Context): Boolean =
        PERMISSIONS.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        val executor = Executors.newSingleThreadExecutor()
        return try {
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    runCatching {
                        manager.getCurrentLocation(provider, signal, executor) { if (cont.isActive) cont.resume(it) }
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }?.let { it.latitude to it.longitude }
        } finally {
            executor.shutdown()
        }
    }
}
