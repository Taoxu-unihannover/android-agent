package ai.miclaw.mvp.app.runtime

import ai.miclaw.mvp.automation.UiSnapshot

object AccessibilitySnapshotStore {
  @Volatile
  private var serviceEnabled: Boolean = false

  @Volatile
  private var latestSnapshot: UiSnapshot? = null

  fun setServiceEnabled(enabled: Boolean) {
    serviceEnabled = enabled
  }

  fun isServiceEnabled(): Boolean = serviceEnabled

  fun update(snapshot: UiSnapshot) {
    latestSnapshot = snapshot
  }

  fun latest(): UiSnapshot? = latestSnapshot
}
