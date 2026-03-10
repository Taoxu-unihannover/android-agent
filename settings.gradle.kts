pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "android-agent"

include(":apps:android-app")
include(":modules:shared-models")
include(":modules:tool-runtime")
include(":modules:policy-engine")
include(":modules:memory-core")
include(":modules:approval-core")
include(":modules:recipe-core")
include(":modules:agent-core")
include(":modules:android-tools")
include(":modules:automation-core")
include(":modules:integration-bridge")
