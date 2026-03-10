plugins {
  id("com.android.application")
  kotlin("android")
}

android {
  namespace = "ai.miclaw.mvp.app"
  compileSdk = 35

  defaultConfig {
    applicationId = "ai.miclaw.mvp.app"
    minSdk = 31
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
    debug {
      isMinifyEnabled = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }
}

dependencies {
  implementation(project(":modules:shared-models"))
  implementation(project(":modules:tool-runtime"))
  implementation(project(":modules:policy-engine"))
  implementation(project(":modules:memory-core"))
  implementation(project(":modules:approval-core"))
  implementation(project(":modules:recipe-core"))
  implementation(project(":modules:agent-core"))
  implementation(project(":modules:android-tools"))
  implementation(project(":modules:automation-core"))
  implementation(project(":modules:integration-bridge"))

  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.activity:activity-ktx:1.9.2")
  implementation("com.google.android.material:material:1.12.0")
}
