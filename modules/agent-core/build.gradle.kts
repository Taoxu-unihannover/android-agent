plugins {
  kotlin("jvm")
}

dependencies {
  implementation(project(":modules:shared-models"))
  implementation(project(":modules:tool-runtime"))
  implementation(project(":modules:policy-engine"))
  implementation(project(":modules:memory-core"))
  implementation(project(":modules:approval-core"))
  implementation(project(":modules:recipe-core"))
}

kotlin {
  jvmToolchain(17)
}
