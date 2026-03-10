package ai.miclaw.mvp.androidtools

import ai.miclaw.mvp.models.Observation
import ai.miclaw.mvp.models.ToolResult
import ai.miclaw.mvp.models.ToolStatus

interface NotificationPort {
  suspend fun listNotifications(): ToolResult
}

interface CalendarPort {
  suspend fun listEvents(): ToolResult

  suspend fun addEvent(args: Map<String, Any?>): ToolResult
}

interface ContactsPort {
  suspend fun searchContacts(args: Map<String, Any?>): ToolResult
}

interface SmsPort {
  suspend fun sendSms(args: Map<String, Any?>): ToolResult
}

interface DevicePort {
  suspend fun deviceStatus(): ToolResult
}

interface CameraPort {
  suspend fun takePhoto(args: Map<String, Any?>): ToolResult
}

interface AppLaunchPort {
  suspend fun launchApp(args: Map<String, Any?>): ToolResult
}

interface UiInspectPort {
  suspend fun inspectUi(): ToolResult
}

fun unimplementedToolResult(
  requestId: String,
  toolName: String,
  message: String,
): ToolResult {
  return ToolResult(
    requestId = requestId,
    toolName = toolName,
    status = ToolStatus.BLOCKED,
    message = message,
    observations = listOf(Observation(source = "android-tools", kind = "stub", summary = message)),
    requiresUserAction = false,
    retryable = false,
  )
}
