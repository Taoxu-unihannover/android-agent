package ai.miclaw.mvp.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import ai.miclaw.mvp.app.runtime.AccessibilitySnapshotStore
import ai.miclaw.mvp.automation.UiBounds
import ai.miclaw.mvp.automation.UiElement
import ai.miclaw.mvp.automation.UiSnapshot

class MiclawAccessibilityService : AccessibilityService() {
  override fun onServiceConnected() {
    super.onServiceConnected()
    AccessibilitySnapshotStore.setServiceEnabled(true)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val root = rootInActiveWindow ?: return
    AccessibilitySnapshotStore.update(
      UiSnapshot(
        packageName = root.packageName?.toString(),
        activityName = event?.className?.toString(),
        nodes = flatten(root),
        pageFingerprint = "${root.packageName}:${event?.className}:${root.childCount}",
      ),
    )
  }

  override fun onInterrupt() {
    AccessibilitySnapshotStore.setServiceEnabled(false)
  }

  private fun flatten(root: AccessibilityNodeInfo): List<UiElement> {
    val elements = mutableListOf<UiElement>()
    fun walk(node: AccessibilityNodeInfo?) {
      if (node == null) return
      val rect = Rect()
      node.getBoundsInScreen(rect)
      elements += UiElement(
        id = "${node.windowId}:${elements.size}",
        resourceId = node.viewIdResourceName,
        className = node.className?.toString(),
        text = node.text?.toString(),
        contentDescription = node.contentDescription?.toString(),
        bounds = UiBounds(rect.left, rect.top, rect.right, rect.bottom),
        clickable = node.isClickable,
        editable = node.isEditable,
        scrollable = node.isScrollable,
        enabled = node.isEnabled,
        selected = node.isSelected,
      )
      for (index in 0 until node.childCount) {
        walk(node.getChild(index))
      }
    }
    walk(root)
    return elements
  }
}
