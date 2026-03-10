package ai.miclaw.mvp.agent

import ai.miclaw.mvp.approval.ApprovalCoordinator
import ai.miclaw.mvp.memory.TaskLogRepository
import ai.miclaw.mvp.models.Observation
import ai.miclaw.mvp.models.PendingApproval
import ai.miclaw.mvp.models.TaskContext
import ai.miclaw.mvp.models.TaskLogEvent
import ai.miclaw.mvp.models.TaskStatus
import ai.miclaw.mvp.models.ToolRequest
import ai.miclaw.mvp.models.ToolResult
import ai.miclaw.mvp.models.ToolStatus
import ai.miclaw.mvp.policy.PolicyDecision
import ai.miclaw.mvp.policy.RiskPolicyEngine
import ai.miclaw.mvp.recipe.RecipeMatchResult
import ai.miclaw.mvp.recipe.RecipeRegistry
import ai.miclaw.mvp.tools.ToolExecutionContext
import ai.miclaw.mvp.tools.ToolDispatcher
import ai.miclaw.mvp.tools.ToolRegistry
import java.util.UUID

sealed interface PlannedStep {
  data class Execute(
    val toolName: String,
    val args: Map<String, Any?> = emptyMap(),
  ) : PlannedStep

  data class AwaitApproval(
    val request: PendingApproval,
  ) : PlannedStep

  data class Completed(
    val summary: String,
  ) : PlannedStep
}

interface PlanningModel {
  suspend fun nextStep(
    context: TaskContext,
    toolRegistry: ToolRegistry,
    matchedRecipe: RecipeMatchResult?,
  ): PlannedStep
}

class ObservationAwarePlanningModel : PlanningModel {
  override suspend fun nextStep(
    context: TaskContext,
    toolRegistry: ToolRegistry,
    matchedRecipe: RecipeMatchResult?,
  ): PlannedStep {
    if (matchedRecipe != null && context.currentStepIndex < matchedRecipe.recipe.steps.size) {
      val step = matchedRecipe.recipe.steps[context.currentStepIndex]
      return PlannedStep.Execute(
        toolName = step.toolName,
        args = step.args + mapOf("goal" to context.goal),
      )
    }

    val goal = context.goal.lowercase()
    val observationKinds = context.observations.map { it.kind }.toSet()
    return when {
      ("打开" in goal || "launch" in goal || "app" in goal) && "app.launch" !in observationKinds ->
        PlannedStep.Execute("app.launch", mapOf("query" to context.goal))
      ("打开" in goal || "launch" in goal || "app" in goal) && "ui.snapshot" !in observationKinds ->
        PlannedStep.Execute("ui.inspect")
      "短信" in goal || "sms" in goal ->
        if ("contacts.search" !in observationKinds) {
          PlannedStep.Execute("contacts.search", mapOf("query" to context.goal))
        } else {
          PlannedStep.Completed("Contact lookup completed; waiting for message drafting or send step.")
        }
      "通知" in goal || "ticket" in goal ->
        if ("notifications.list" !in observationKinds) {
          PlannedStep.Execute("notifications.list")
        } else if ("calendar.write" !in observationKinds && ("日历" in goal || "提醒" in goal)) {
          PlannedStep.Execute("calendar.add", mapOf("summary" to context.goal))
        } else {
          PlannedStep.Completed("Notification observation completed.")
        }
      "日历" in goal || "会议" in goal || "calendar" in goal ->
        if ("calendar.write" !in observationKinds) {
          PlannedStep.Execute("calendar.add", mapOf("summary" to context.goal))
        } else {
          PlannedStep.Completed("Calendar action already completed.")
        }
      "界面" in goal || "点击" in goal || "ui" in goal ->
        PlannedStep.Execute("ui.inspect")
      else ->
        PlannedStep.Completed("No matching MVP flow found for current goal.")
    }
  }
}

data class RunTaskResult(
  val status: TaskStatus,
  val summary: String,
  val lastResult: ToolResult? = null,
)

class AgentOrchestrator(
  private val planner: PlanningModel,
  private val toolRegistry: ToolRegistry,
  private val toolDispatcher: ToolDispatcher,
  private val policyEngine: RiskPolicyEngine,
  private val approvalCoordinator: ApprovalCoordinator,
  private val recipeRegistry: RecipeRegistry,
  private val taskLogRepository: TaskLogRepository,
) {
  suspend fun runTask(
    taskId: String,
    sessionId: String,
    goal: String,
    maxSteps: Int = 4,
    executionContext: ToolExecutionContext = ToolExecutionContext(
      sessionId = sessionId,
      taskId = taskId,
    ),
  ): RunTaskResult {
    var context = TaskContext(
      taskId = taskId,
      sessionId = sessionId,
      goal = goal,
      status = TaskStatus.RUNNING,
    )
    var lastResult: ToolResult? = null

    repeat(maxSteps) {
      approvalCoordinator.pending(taskId)?.let { pending ->
        return RunTaskResult(
          status = TaskStatus.WAITING_FOR_APPROVAL,
          summary = pending.summary,
          lastResult = lastResult,
        )
      }

      val matchedRecipe = recipeRegistry.match(context.goal, context.observations)
      if (matchedRecipe != null && matchedRecipe.recipe.id != context.activeRecipeId) {
        taskLogRepository.append(
          TaskLogEvent(
            taskId = taskId,
            type = "recipe.matched",
            message = matchedRecipe.reason,
            payload = mapOf(
              "recipeId" to matchedRecipe.recipe.id,
              "confidence" to matchedRecipe.confidence,
            ),
          ),
        )
      }

      val next = planner.nextStep(context, toolRegistry, matchedRecipe)
      when (next) {
        is PlannedStep.Completed -> {
          taskLogRepository.append(
            TaskLogEvent(taskId, "task.completed", next.summary),
          )
          return RunTaskResult(TaskStatus.COMPLETED, next.summary, lastResult)
        }

        is PlannedStep.AwaitApproval -> {
          taskLogRepository.append(
            TaskLogEvent(
              taskId = taskId,
              type = "approval.requested",
              message = next.request.summary,
              payload = mapOf("toolName" to next.request.toolName),
            ),
          )
          return RunTaskResult(
            status = TaskStatus.WAITING_FOR_APPROVAL,
            summary = next.request.summary,
            lastResult = lastResult,
          )
        }

        is PlannedStep.Execute -> {
          val definition = toolRegistry.resolve(next.toolName)?.definition
            ?: error("Unknown tool ${next.toolName}")
          val policy = policyEngine.evaluate(definition)
          maybeAppendPolicyLog(taskId, definition.name, policy)
          if (!policy.allowed) {
            return RunTaskResult(
              status = TaskStatus.FAILED,
              summary = "Tool ${definition.name} is not allowed in MVP.",
              lastResult = lastResult,
            )
          }
          if (policy.requiresApproval) {
            val request = approvalCoordinator.request(
              taskId = taskId,
              toolName = definition.name,
              riskLevel = definition.riskLevel,
              summary = "Approval required for ${definition.name}",
            )
            return RunTaskResult(
              status = TaskStatus.WAITING_FOR_APPROVAL,
              summary = request.summary,
              lastResult = lastResult,
            )
          }

          val request = ToolRequest(
            requestId = UUID.randomUUID().toString(),
            idempotencyKey = UUID.randomUUID().toString(),
            toolName = next.toolName,
            args = next.args,
            sessionId = sessionId,
            taskId = taskId,
          )
          lastResult = toolDispatcher.dispatch(request, executionContext)
          taskLogRepository.append(
            TaskLogEvent(
              taskId = taskId,
              type = "tool.result",
              message = lastResult.message,
              payload = mapOf(
                "toolName" to lastResult.toolName,
                "status" to lastResult.status.name,
              ),
            ),
          )
          context = context.copy(
            currentStepIndex = context.currentStepIndex + 1,
            activeRecipeId = matchedRecipe?.recipe?.id ?: context.activeRecipeId,
            observations = context.observations + lastResult.observations,
            status = if (lastResult.status == ToolStatus.SUCCESS) TaskStatus.RUNNING else TaskStatus.FAILED,
          )
          if (lastResult.status != ToolStatus.SUCCESS) {
            return RunTaskResult(
              status = context.status,
              summary = lastResult.message,
              lastResult = lastResult,
            )
          }
        }
      }
    }

    return RunTaskResult(
      status = TaskStatus.FAILED,
      summary = "Task stopped after reaching maxSteps.",
      lastResult = lastResult,
    )
  }

  private fun maybeAppendPolicyLog(taskId: String, toolName: String, decision: PolicyDecision) {
    taskLogRepository.append(
      TaskLogEvent(
        taskId = taskId,
        type = "policy.checked",
        message = "Policy evaluated for $toolName",
        payload = mapOf(
          "allowed" to decision.allowed,
          "requiresApproval" to decision.requiresApproval,
          "reason" to decision.reason,
        ),
      ),
    )
  }
}

fun observation(source: String, kind: String, summary: String): Observation {
  return Observation(source = source, kind = kind, summary = summary)
}
