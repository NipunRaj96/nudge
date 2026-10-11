package com.nudge.app.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

/** What stands between the app and an accurate location fix. */
enum class LocationProblem { Denied, Approximate, Off }

fun locationProblem(ctx: Context): LocationProblem? {
  fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
  return when {
    // Android 12+ lets people grant "approximate" only: that can be kilometres off, so it counts as a problem.
    !granted(Manifest.permission.ACCESS_FINE_LOCATION) ->
      if (granted(Manifest.permission.ACCESS_COARSE_LOCATION)) LocationProblem.Approximate else LocationProblem.Denied
    !LocationManagerCompat.isLocationEnabled(ctx.getSystemService(android.location.LocationManager::class.java)) -> LocationProblem.Off
    else -> null
  }
}

val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Makes sure the app has precise location permission and the phone's location is switched on, then runs the action.
 * Asks for the permission first; if it's refused, set to approximate, or location is off, explains and opens the right
 * settings screen, and carries on by itself when the person comes back with it fixed.
 * [skip] (e.g. "Start anyway") runs the action without location; null just closes the explanation.
 */
@Composable
fun rememberLocationGate(why: String, permissions: Array<String> = locationPermissions, skip: String? = null): (onReady: () -> Unit) -> Unit {
  val ctx = LocalContext.current
  var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
  var problem by remember { mutableStateOf<LocationProblem?>(null) }
  var inSettings by remember { mutableStateOf(false) }

  fun check() {
    val action = pending ?: return
    problem = locationProblem(ctx)
    if (problem == null) { pending = null; action() }
  }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { check() }
  // Keyed on Unit: a key change would re-run this at once (still resumed) instead of on the return from Settings.
  LifecycleResumeEffect(Unit) {
    if (inSettings) { inSettings = false; check() }
    onPauseOrDispose {}
  }

  problem?.let { p ->
    val (title, body) = when (p) {
      LocationProblem.Denied -> "Allow location" to "$why Open Settings > Permissions > Location and choose \"Allow only while using the app\"."
      LocationProblem.Approximate -> "Turn on precise location" to "Nudge only has your approximate location, which can be a few km off. $why Open Settings > Permissions > Location and turn on \"Use precise location\"."
      LocationProblem.Off -> "Turn on location" to "Your phone's location is switched off. $why"
    }
    fun close() { problem = null; pending = null }
    AlertDialog(
      onDismissRequest = ::close,
      containerColor = NudgeColors.SurfaceContainerHigh,
      title = { Text(title, style = NudgeType.HeadlineMd, color = NudgeColors.OnSurface) },
      text = { Text(body, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant) },
      confirmButton = {
        TextButton({
          problem = null
          inSettings = true
          ctx.startActivity(
            if (p == LocationProblem.Off) Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
          )
        }) { Text("Open settings", color = NudgeColors.Primary) }
      },
      dismissButton = {
        TextButton({ val action = pending; close(); if (skip != null) action?.invoke() }) { Text(skip ?: "Not now", color = NudgeColors.Primary) }
      },
    )
  }

  return { onReady ->
    pending = onReady
    // The system dialog only appears while it's still allowed to; after "Don't ask again" it returns at once and the
    // explanation above points to Settings instead.
    if (permissions.any { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }) ask.launch(permissions) else check()
  }
}
