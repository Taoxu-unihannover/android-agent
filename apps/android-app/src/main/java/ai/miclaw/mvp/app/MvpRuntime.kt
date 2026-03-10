package ai.miclaw.mvp.app

import android.content.Context
import androidx.core.content.ContextCompat
import ai.miclaw.mvp.agent.AgentOrchestrator
import ai.miclaw.mvp.agent.ObservationAwarePlanningModel
import ai.miclaw.mvp.approval.ApprovalCoordinator
import ai.miclaw.mvp.approval.InMemoryApprovalStore
import ai.miclaw.mvp.app.runtime.AndroidToolCatalog
import ai.miclaw.mvp.app.runtime.AccessibilitySnapshotStore
import ai.miclaw.mvp.app.runtime.AppLaunchToolHandler
import ai.miclaw.mvp.app.runtime.CalendarAddToolHandler
import ai.miclaw.mvp.app.runtime.ContactsSearchToolHandler
import ai.miclaw.mvp.app.runtime.DeviceStatusToolHandler
import ai.miclaw.mvp.app.runtime.NotificationsListToolHandler
import ai.miclaw.mvp.app.runtime.StubToolHandler
import ai.miclaw.mvp.app.runtime.UiInspectToolHandler
import ai.miclaw.mvp.memory.InMemoryTaskLogRepository
import ai.miclaw.mvp.recipe.Recipe
import ai.miclaw.mvp.recipe.RecipeStep
import ai.miclaw.mvp.recipe.StaticRecipeRegistry
import ai.miclaw.mvp.tools.ToolExecutionContext
import ai.miclaw.mvp.tools.ToolDispatcher
import ai.miclaw.mvp.tools.ToolHandler
import ai.miclaw.mvp.tools.ToolRegistry
import ai.miclaw.mvp.policy.RiskPolicyEngine

data class RuntimeComponents(
  val registry: ToolRegistry,
  val orchestrator: AgentOrchestrator,
  val logRepository: InMemoryTaskLogRepository,
  val approvalCoordinator: ApprovalCoordinator,
  val defaultExecutionContext: ToolExecutionContext,
)

private fun buildRecipeRegistry(): StaticRecipeRegistry {
  return StaticRecipeRegistry(
    recipes = listOf(
      Recipe(
        id = "open-app-and-inspect",
        name = "Open app and inspect page",
        matchKeywords = listOf("打开", "open", "app"),
        steps = listOf(
          RecipeStep(id = "launch-app", toolName = "app.launch"),
          RecipeStep(id = "inspect-ui", toolName = "ui.inspect"),
        ),
      ),
      Recipe(
        id = "find-contact-before-sms",
        name = "Find contact before sms",
        matchKeywords = listOf("短信", "sms"),
        steps = listOf(
          RecipeStep(id = "search-contact", toolName = "contacts.search"),
        ),
      ),
    ),
  )
}

private fun buildExecutionContext(
  appContext: Context,
  sessionId: String,
  taskId: String,
  isForeground: Boolean = true,
): ToolExecutionContext {
  val trackedPermissions = setOf(
    android.Manifest.permission.READ_CONTACTS,
    android.Manifest.permission.READ_CALENDAR,
    android.Manifest.permission.WRITE_CALENDAR,
    android.Manifest.permission.SEND_SMS,
    android.Manifest.permission.CAMERA,
  )
  val grantedPermissions = trackedPermissions.filterTo(mutableSetOf()) { permission ->
    ContextCompat.checkSelfPermission(appContext, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
  }
  val enabledServices = buildSet {
    add("notification-listener")
    if (AccessibilitySnapshotStore.isServiceEnabled()) {
      add("accessibility")
    }
  }
  return ToolExecutionContext(
    sessionId = sessionId,
    taskId = taskId,
    isForeground = isForeground,
    grantedPermissions = grantedPermissions,
    enabledServices = enabledServices,
  )
}

object MvpRuntime {
  @Volatile
  private var runtime: RuntimeComponents? = null

  fun get(context: Context): RuntimeComponents {
    return runtime ?: synchronized(this) {
      runtime ?: create(context.applicationContext).also { runtime = it }
    }
  }

  private fun create(appContext: Context): RuntimeComponents {
    val handlers: List<ToolHandler> = listOf(
      DeviceStatusToolHandler(appContext),
      AppLaunchToolHandler(appContext),
      NotificationsListToolHandler(),
      CalendarAddToolHandler(appContext),
      ContactsSearchToolHandler(appContext),
      UiInspectToolHandler(),
      StubToolHandler(AndroidToolCatalog.notificationsAction),
      StubToolHandler(AndroidToolCatalog.calendarEvents),
      StubToolHandler(AndroidToolCatalog.smsSend),
      StubToolHandler(AndroidToolCatalog.cameraSnap),
      StubToolHandler(AndroidToolCatalog.screenCapture),
      StubToolHandler(AndroidToolCatalog.uiAct),
    )
    val registry = ToolRegistry(handlers)
    val logRepository = InMemoryTaskLogRepository()
    val approvalCoordinator = ApprovalCoordinator(InMemoryApprovalStore())
    val executionContext = buildExecutionContext(
      appContext = appContext,
      sessionId = "main",
      taskId = "preview",
      isForeground = true,
    )

    val orchestrator = AgentOrchestrator(
      planner = ObservationAwarePlanningModel(),
      toolRegistry = registry,
      toolDispatcher = ToolDispatcher(registry),
      policyEngine = RiskPolicyEngine(),
      approvalCoordinator = approvalCoordinator,
      recipeRegistry = buildRecipeRegistry(),
      taskLogRepository = logRepository,
    )
    return RuntimeComponents(
      registry = registry,
      orchestrator = orchestrator,
      logRepository = logRepository,
      approvalCoordinator = approvalCoordinator,
      defaultExecutionContext = executionContext,
    )
  }
}
