package ai.miclaw.mvp.automation

data class UiBounds(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
)

data class UiElement(
  val id: String,
  val resourceId: String? = null,
  val className: String? = null,
  val text: String?,
  val contentDescription: String? = null,
  val bounds: UiBounds? = null,
  val clickable: Boolean = false,
  val editable: Boolean = false,
  val scrollable: Boolean = false,
  val enabled: Boolean = true,
  val selected: Boolean = false,
)

data class UiSnapshot(
  val packageName: String?,
  val activityName: String?,
  val nodes: List<UiElement>,
  val pageFingerprint: String? = null,
)

data class ScreenObservation(
  val uiSnapshot: UiSnapshot,
  val screenshotAvailable: Boolean = false,
  val ocrText: String? = null,
)

data class UiActionRequest(
  val action: String,
  val targetId: String? = null,
  val text: String? = null,
)

data class UiActionResult(
  val success: Boolean,
  val message: String,
  val updatedSnapshot: UiSnapshot? = null,
)

interface UiAutomationFacade {
  suspend fun inspect(): UiSnapshot

  suspend fun act(request: UiActionRequest): UiActionResult
}
