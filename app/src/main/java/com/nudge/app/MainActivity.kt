package com.nudge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nudge.app.nav.NudgeApp
import com.nudge.app.ui.theme.NudgeTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    com.nudge.app.data.Backends.init(this)
    setContent { NudgeTheme { NudgeApp(devStart = if (BuildConfig.DEBUG) intent.getStringExtra("screen") else null) } }
  }
}
