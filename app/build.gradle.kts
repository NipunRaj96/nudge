import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.nudge.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.nudge.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") } // real phones only; halves the MapLibre native payload
    // Backend: set nudge.supabaseUrl / nudge.supabaseKey in local.properties (or NUDGE_SUPABASE_URL/KEY env).
    // Empty URL = offline demo mode (in-memory FakeBackend).
    val props = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { props.load(it) }
    fun cfg(name: String, env: String) = System.getenv(env) ?: props.getProperty(name) ?: "" // env wins: test builds against the local stack
    buildConfigField("String", "SUPABASE_URL", "\"${cfg("nudge.supabaseUrl", "NUDGE_SUPABASE_URL")}\"")
    buildConfigField("String", "SUPABASE_KEY", "\"${cfg("nudge.supabaseKey", "NUDGE_SUPABASE_KEY")}\"")
  }

  signingConfigs {
    // Release signing comes from the environment only; debug uses the default Android debug keystore.
    val ks = System.getenv("KEYSTORE_PATH")
    if (ks != null) {
      create("release") {
        storeFile = file(ks)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

// postgrest-kt drags in full kotlin-reflect for KProperty filters (we only use string column names). With it on the
// classpath every typeOf<T>() decode goes through slow reflection (multi-second stalls on cold start).
configurations.matching { it.name.endsWith("RuntimeClasspath") || it.name.endsWith("CompileClasspath") }
  .configureEach { exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect") }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.zxing.core)
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.auth)
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.realtime)
  implementation(libs.supabase.functions)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.maplibre)
  implementation(libs.pdfbox) // operator imports route timetables from PDF
  testImplementation(libs.junit)
}
