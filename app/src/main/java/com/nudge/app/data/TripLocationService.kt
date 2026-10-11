package com.nudge.app.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nudge.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shares the driver phone's GPS while a trip is live: one point every ~5 s / 10 m into `positions`.
 * Foreground service so it keeps running with the screen off. Plain LocationManager (no Play Services).
 */
class TripLocationService : Service(), LocationListener {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var tripId: String? = null
  private var startedAt = 0L
  @Volatile private var failures = 0

  override fun onBind(intent: Intent?) = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Backends.init(this)
    val id = intent?.getStringExtra(EXTRA_TRIP) ?: return START_NOT_STICKY.also { stopSelf() }
    if (!hasPermission(this)) return START_NOT_STICKY.also { stopSelf() }
    val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
    try {
      ServiceCompat.startForeground(this, 1, notification(), type)
    } catch (e: Exception) { // Android 12+: not allowed from the background; the app restarts it when opened
      stopSelf(); return START_NOT_STICKY
    }
    if (tripId != id) {
      tripId = id
      startedAt = System.currentTimeMillis()
      val lm = getSystemService(LocationManager::class.java)
      lm.removeUpdates(this)
      try {
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000L, 10f, this, Looper.getMainLooper())
      } catch (e: SecurityException) {
        stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  override fun onLocationChanged(l: Location) {
    val id = tripId ?: return
    // Skip rough fixes (indoors, first seconds after start): they make the shuttle jump to the wrong street.
    if (l.hasAccuracy() && l.accuracy > 60f) return
    // Safety net: a driver who never taps End trip doesn't share GPS all day.
    if (System.currentTimeMillis() - startedAt > 6 * 3600_000L) { stopSelf(); return }
    // ponytail: a point that fails to upload (no signal) is dropped; queue them if playback gaps matter.
    scope.launch {
      try {
        Backends.instance.sendPosition(id, l.latitude, l.longitude, if (l.hasSpeed()) l.speed * 3.6f else null, if (l.hasBearing()) l.bearing else null)
        failures = 0
      } catch (e: BackendError) {
        // The server refuses points once the trip is no longer live (ended elsewhere): stop instead of retrying forever.
        if (++failures >= 20) stopSelf()
      }
    }
  }

  override fun onDestroy() {
    getSystemService(LocationManager::class.java).removeUpdates(this)
    scope.cancel()
    super.onDestroy()
  }

  private fun notification(): Notification {
    val nm = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Live trip", NotificationManager.IMPORTANCE_LOW))
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    return Notification.Builder(this, CHANNEL)
      .setSmallIcon(android.R.drawable.ic_menu_mylocation)
      .setContentTitle("Trip in progress")
      .setContentText("Riders can see the shuttle on the map")
      .setOngoing(true)
      .setContentIntent(open)
      .build()
  }

  companion object {
    private const val CHANNEL = "trip"
    private const val EXTRA_TRIP = "trip"
    val permissions = buildList {
      add(Manifest.permission.ACCESS_FINE_LOCATION)
      add(Manifest.permission.ACCESS_COARSE_LOCATION)
      if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun hasPermission(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun start(ctx: Context, tripId: String) =
      ContextCompat.startForegroundService(ctx, Intent(ctx, TripLocationService::class.java).putExtra(EXTRA_TRIP, tripId))

    fun stop(ctx: Context) = ctx.stopService(Intent(ctx, TripLocationService::class.java))
  }
}
