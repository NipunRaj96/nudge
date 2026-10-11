package com.nudge.app.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Route {
  data object Login : Route
  data object Otp : Route
  data object Name : Route
  data object PassengerRoot : Route
  data object DriverRoot : Route
  data object AdminRoot : Route
  data object Plans : Route
  data object Pay : Route
  data class BoardingPass(val rideId: String) : Route
  data class Tracking(val rideId: String) : Route
  data class Playback(val shuttleCode: String, val routeName: String, val tripId: String?, val routeId: String?, val evening: Boolean) : Route
  // Operator management
  data object AdminUpi : Route
  data object AdminPlans : Route
  data object AdminStaff : Route
  data object AdminShuttles : Route
  data object AdminRules : Route
  /** Blank id = new route. */
  data class RouteEditor(val routeId: String) : Route
  data object MapPicker : Route
}

/** Minimal back stack. `forward` tells the transition which way to slide. */
class Navigator(start: Route) {
  val stack = mutableStateListOf(start)
  var forward by mutableStateOf(true)
    private set
  val top: Route get() = stack.last()
  val canPop: Boolean get() = stack.size > 1

  fun push(r: Route) { forward = true; stack.add(r) }
  fun pop() { if (canPop) { forward = false; stack.removeAt(stack.lastIndex) } }
  fun popTo(r: Route) { forward = false; while (stack.size > 1 && stack.last() != r) stack.removeAt(stack.lastIndex) }
  fun replaceAll(r: Route) { forward = true; stack.clear(); stack.add(r) }
}
