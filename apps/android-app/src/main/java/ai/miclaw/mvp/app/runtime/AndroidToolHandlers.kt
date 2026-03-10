package ai.miclaw.mvp.app.runtime

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import ai.miclaw.mvp.androidtools.unimplementedToolResult
import ai.miclaw.mvp.models.Observation
import ai.miclaw.mvp.models.RiskLevel
import ai.miclaw.mvp.models.ToolCategory
import ai.miclaw.mvp.models.ToolDefinition
import ai.miclaw.mvp.models.ToolIdempotencyPolicy
import ai.miclaw.mvp.models.ToolPrecondition
import ai.miclaw.mvp.models.ToolPreconditionKind
import ai.miclaw.mvp.models.ToolRequest
import ai.miclaw.mvp.models.ToolResult
import ai.miclaw.mvp.models.ToolStatus
import ai.miclaw.mvp.tools.ToolExecutionContext
import ai.miclaw.mvp.tools.ToolHandler
import java.util.TimeZone

object AndroidToolCatalog {
  val notificationsList = ToolDefinition(
    name = "notifications.list",
    description = "List device notifications",
    category = ToolCategory.SYSTEM,
    riskLevel = RiskLevel.L0,
    capabilityGroup = "notifications",
    inputSchemaHint = "{}",
    outputSchemaHint = "{ notifications: [...] }",
  )
  val notificationsAction = ToolDefinition(
    name = "notifications.action",
    description = "Reply, dismiss, or open notification",
    category = ToolCategory.COMMUNICATION,
    riskLevel = RiskLevel.L2,
    capabilityGroup = "notifications",
    requiresConfirmation = true,
  )
  val calendarEvents = ToolDefinition(
    name = "calendar.events",
    description = "List calendar events",
    category = ToolCategory.API,
    riskLevel = RiskLevel.L0,
    capabilityGroup = "calendar",
    preconditions = listOf(
      ToolPrecondition(
        kind = ToolPreconditionKind.PERMISSION_GRANTED,
        key = Manifest.permission.READ_CALENDAR,
        description = "Calendar read permission is required.",
      ),
    ),
  )
  val calendarAdd = ToolDefinition(
    name = "calendar.add",
    description = "Create calendar event",
    category = ToolCategory.API,
    riskLevel = RiskLevel.L1,
    capabilityGroup = "calendar",
    preconditions = listOf(
      ToolPrecondition(
        kind = ToolPreconditionKind.PERMISSION_GRANTED,
        key = Manifest.permission.WRITE_CALENDAR,
        description = "Calendar write permission is required.",
      ),
    ),
  )
  val contactsSearch = ToolDefinition(
    name = "contacts.search",
    description = "Search contacts",
    category = ToolCategory.API,
    riskLevel = RiskLevel.L0,
    capabilityGroup = "contacts",
    preconditions = listOf(
      ToolPrecondition(
        kind = ToolPreconditionKind.PERMISSION_GRANTED,
        key = Manifest.permission.READ_CONTACTS,
        description = "Contacts permission is required.",
      ),
    ),
  )
  val smsSend = ToolDefinition(
    name = "sms.send",
    description = "Send SMS",
    category = ToolCategory.COMMUNICATION,
    riskLevel = RiskLevel.L2,
    capabilityGroup = "messaging",
    requiresConfirmation = true,
    preconditions = listOf(
      ToolPrecondition(
        kind = ToolPreconditionKind.PERMISSION_GRANTED,
        key = Manifest.permission.SEND_SMS,
        description = "SMS permission is required.",
      ),
    ),
  )
  val deviceStatus = ToolDefinition(
    name = "device.status",
    description = "Read device status",
    category = ToolCategory.SYSTEM,
    riskLevel = RiskLevel.L0,
    capabilityGroup = "device",
  )
  val cameraSnap = ToolDefinition(
    name = "camera.snap",
    description = "Take a photo",
    category = ToolCategory.MEDIA,
    riskLevel = RiskLevel.L2,
    capabilityGroup = "camera",
    requiresForeground = true,
    requiresConfirmation = true,
    preconditions = listOf(
      ToolPrecondition(
        kind = ToolPreconditionKind.PERMISSION_GRANTED,
        key = Manifest.permission.CAMERA,
        description = "Camera permission is required.",
      ),
    ),
  )
  val screenCapture = ToolDefinition(
    name = "screen.capture",
    description = "Capture current screen",
    category = ToolCategory.MEDIA,
    riskLevel = RiskLevel.L1,
    capabilityGroup = "capture",
    requiresForeground = true,
    requiresConfirmation = true,
  )
  val appLaunch = ToolDefinition(
    name = "app.launch",
    description = "Launch target app",
    category = ToolCategory.SYSTEM,
    riskLevel = RiskLevel.L1,
    capabilityGroup = "navigation",
    inputSchemaHint = "{ query?: string, packageName?: string }",
    outputSchemaHint = "{ launchedPackage: string }",
    idempotencyPolicy = ToolIdempotencyPolicy.PER_TASK,
  )
  val uiInspect = ToolDefinition(
    name = "ui.inspect",
    description = "Inspect visible UI tree",
    category = ToolCategory.UI,
    riskLevel = RiskLevel.L0,
    capabilityGroup = "ui-observation",
    requiresForeground = true,
    preconditions = listOf(
      ToolPrecondition(
        kind = ToolPreconditionKind.SERVICE_ENABLED,
        key = "accessibility",
        description = "Accessibility service must be enabled for UI inspection.",
      ),
    ),
  )
  val uiAct = ToolDefinition(
    name = "ui.act",
    description = "Perform UI action",
    category = ToolCategory.UI,
    riskLevel = RiskLevel.L2,
    capabilityGroup = "ui-automation",
    requiresForeground = true,
    requiresConfirmation = true,
    preconditions = listOf(
      ToolPrecondition(
        kind = ToolPreconditionKind.SERVICE_ENABLED,
        key = "accessibility",
        description = "Accessibility service must be enabled for UI actions.",
      ),
    ),
  )

  fun all(): List<ToolDefinition> = listOf(
    notificationsList,
    notificationsAction,
    calendarEvents,
    calendarAdd,
    contactsSearch,
    smsSend,
    deviceStatus,
    cameraSnap,
    screenCapture,
    appLaunch,
    uiInspect,
    uiAct,
  )
}

abstract class BaseToolHandler(
  override val definition: ToolDefinition,
) : ToolHandler {
  protected fun success(
    request: ToolRequest,
    message: String,
    payload: Map<String, Any?> = emptyMap(),
    observations: List<Observation> = emptyList(),
  ): ToolResult {
    return ToolResult(
      requestId = request.requestId,
      toolName = request.toolName,
      status = ToolStatus.SUCCESS,
      message = message,
      payload = payload,
      observations = observations,
    )
  }
}

class DeviceStatusToolHandler(
  private val appContext: Context,
) : BaseToolHandler(AndroidToolCatalog.deviceStatus) {
  override suspend fun handle(request: ToolRequest, context: ToolExecutionContext): ToolResult {
    val batteryStatus = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else null
    val payload = mapOf(
      "manufacturer" to Build.MANUFACTURER,
      "model" to Build.MODEL,
      "sdkInt" to Build.VERSION.SDK_INT,
      "release" to Build.VERSION.RELEASE,
      "batteryPercent" to batteryPct,
      "isForeground" to context.isForeground,
    )
    return success(
      request = request,
      message = "Device status collected.",
      payload = payload,
      observations = listOf(
        Observation(
          source = "device",
          kind = "device.status",
          summary = "Collected model, sdk, and battery information.",
          payload = payload,
        ),
      ),
    )
  }
}

class AppLaunchToolHandler(
  private val appContext: Context,
) : BaseToolHandler(AndroidToolCatalog.appLaunch) {
  override suspend fun handle(request: ToolRequest, context: ToolExecutionContext): ToolResult {
    val packageName = request.args["packageName"] as? String
    val query = (request.args["query"] as? String)?.trim().orEmpty()
    val pm = appContext.packageManager
    val resolvedPackage = packageName?.takeIf { it.isNotBlank() } ?: resolvePackageFromQuery(pm, query)
    if (resolvedPackage.isNullOrBlank()) {
      return unimplementedToolResult(
        requestId = request.requestId,
        toolName = request.toolName,
        message = "Could not resolve an app from query.",
      )
    }
    val launchIntent = pm.getLaunchIntentForPackage(resolvedPackage)
      ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      ?: return unimplementedToolResult(
        requestId = request.requestId,
        toolName = request.toolName,
        message = "Launch intent not found for $resolvedPackage.",
      )
    appContext.startActivity(launchIntent)
    return success(
      request = request,
      message = "Launched app $resolvedPackage.",
      payload = mapOf("launchedPackage" to resolvedPackage),
      observations = listOf(
        Observation(
          source = "app",
          kind = "app.launch",
          summary = "Launched $resolvedPackage.",
          payload = mapOf("packageName" to resolvedPackage),
        ),
      ),
    )
  }

  private fun resolvePackageFromQuery(
    packageManager: android.content.pm.PackageManager,
    query: String,
  ): String? {
    if (query.isBlank()) return null
    val normalized = query.lowercase()
    val packages = packageManager.getInstalledApplications(0)
    return packages.firstOrNull { appInfo ->
      val label = packageManager.getApplicationLabel(appInfo).toString().lowercase()
      appInfo.packageName.lowercase().contains(normalized) || label.contains(normalized)
    }?.packageName
  }
}

class NotificationsListToolHandler : BaseToolHandler(AndroidToolCatalog.notificationsList) {
  override suspend fun handle(request: ToolRequest, context: ToolExecutionContext): ToolResult {
    val notifications = NotificationEventStore.list()
    val payload = mapOf(
      "count" to notifications.size,
      "notifications" to notifications.map {
        mapOf(
          "packageName" to it.packageName,
          "title" to it.title,
          "text" to it.text,
          "postedAtMs" to it.postedAtMs,
        )
      },
    )
    return success(
      request = request,
      message = "Read ${notifications.size} notifications.",
      payload = payload,
      observations = listOf(
        Observation(
          source = "notifications",
          kind = "notifications.list",
          summary = "Collected recent notifications.",
          payload = payload,
        ),
      ),
    )
  }
}

class CalendarAddToolHandler(
  private val appContext: Context,
) : BaseToolHandler(AndroidToolCatalog.calendarAdd) {
  override suspend fun handle(request: ToolRequest, context: ToolExecutionContext): ToolResult {
    val resolver = appContext.contentResolver
    val calendarId = queryWritableCalendarId() ?: return unimplementedToolResult(
      requestId = request.requestId,
      toolName = request.toolName,
      message = "No writable calendar found on device.",
    )
    val summary = (request.args["summary"] as? String)?.ifBlank { null } ?: "Android Agent Event"
    val startAt = System.currentTimeMillis() + 3_600_000
    val endAt = startAt + 3_600_000
    val values = ContentValues().apply {
      put(CalendarContract.Events.CALENDAR_ID, calendarId)
      put(CalendarContract.Events.TITLE, summary)
      put(CalendarContract.Events.DTSTART, startAt)
      put(CalendarContract.Events.DTEND, endAt)
      put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
    }
    val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return unimplementedToolResult(
      requestId = request.requestId,
      toolName = request.toolName,
      message = "Failed to insert calendar event.",
    )
    return success(
      request = request,
      message = "Calendar event created.",
      payload = mapOf("eventUri" to uri.toString(), "title" to summary),
      observations = listOf(
        Observation(
          source = "calendar",
          kind = "calendar.write",
          summary = "Created calendar event $summary.",
          payload = mapOf("eventUri" to uri.toString()),
        ),
      ),
    )
  }

  private fun queryWritableCalendarId(): Long? {
    val projection = arrayOf(CalendarContract.Calendars._ID)
    val selection = "${CalendarContract.Calendars.VISIBLE}=1"
    appContext.contentResolver.query(
      CalendarContract.Calendars.CONTENT_URI,
      projection,
      selection,
      null,
      null,
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        return cursor.getLong(0)
      }
    }
    return null
  }
}

class ContactsSearchToolHandler(
  private val appContext: Context,
) : BaseToolHandler(AndroidToolCatalog.contactsSearch) {
  override suspend fun handle(request: ToolRequest, context: ToolExecutionContext): ToolResult {
    val query = ((request.args["query"] as? String) ?: "").trim()
    val results = mutableListOf<Map<String, Any?>>()
    val projection = arrayOf(
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
      ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    val selection = if (query.isNotBlank()) {
      "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
    } else {
      null
    }
    val args = if (query.isNotBlank()) arrayOf("%$query%") else null
    appContext.contentResolver.query(
      ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
      projection,
      selection,
      args,
      "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
    )?.use { cursor ->
      while (cursor.moveToNext() && results.size < 5) {
        results += mapOf(
          "displayName" to cursor.getString(0),
          "number" to cursor.getString(1),
        )
      }
    }
    return success(
      request = request,
      message = "Found ${results.size} contacts.",
      payload = mapOf("contacts" to results),
      observations = listOf(
        Observation(
          source = "contacts",
          kind = "contacts.search",
          summary = "Completed contact lookup.",
          payload = mapOf("count" to results.size),
        ),
      ),
    )
  }
}

class UiInspectToolHandler : BaseToolHandler(AndroidToolCatalog.uiInspect) {
  override suspend fun handle(request: ToolRequest, context: ToolExecutionContext): ToolResult {
    val snapshot = AccessibilitySnapshotStore.latest() ?: return unimplementedToolResult(
      requestId = request.requestId,
      toolName = request.toolName,
      message = "No accessibility snapshot available yet.",
    )
    val payload = mapOf(
      "packageName" to snapshot.packageName,
      "activityName" to snapshot.activityName,
      "pageFingerprint" to snapshot.pageFingerprint,
      "nodeCount" to snapshot.nodes.size,
      "nodes" to snapshot.nodes.map {
        mapOf(
          "id" to it.id,
          "resourceId" to it.resourceId,
          "className" to it.className,
          "text" to it.text,
          "contentDescription" to it.contentDescription,
          "clickable" to it.clickable,
          "editable" to it.editable,
          "scrollable" to it.scrollable,
          "enabled" to it.enabled,
          "selected" to it.selected,
        )
      },
    )
    return success(
      request = request,
      message = "UI snapshot collected.",
      payload = payload,
      observations = listOf(
        Observation(
          source = "ui",
          kind = "ui.snapshot",
          summary = "Collected ${snapshot.nodes.size} visible UI elements.",
          payload = mapOf(
            "packageName" to snapshot.packageName,
            "activityName" to snapshot.activityName,
          ),
        ),
      ),
    )
  }
}

class StubToolHandler(
  override val definition: ToolDefinition,
) : ToolHandler {
  override suspend fun handle(request: ToolRequest, context: ToolExecutionContext): ToolResult {
    return unimplementedToolResult(
      requestId = request.requestId,
      toolName = request.toolName,
      message = "${request.toolName} is reserved in the current MVP stage.",
    )
  }
}
