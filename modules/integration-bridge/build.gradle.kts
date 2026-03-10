plugins {
  kotlin("jvm")
}

dependencies {
  implementation(project(":modules:shared-models"))
}

kotlin {
  jvmToolchain(17)
}
