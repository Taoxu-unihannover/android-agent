package ai.miclaw.mvp.models

enum class RiskLevel {
  L0,
  L1,
  L2,
  L3,
}

enum class ToolCategory {
  SYSTEM,
  API,
  UI,
  MEDIA,
  COMMUNICATION,
}

enum class ToolPreconditionKind {
  FOREGROUND,
  PERMISSION_GRANTED,
  SERVICE_ENABLED,
  CONNECTOR_READY,
}

enum class ToolIdempotencyPolicy {
  PER_REQUEST,
  PER_TASK,
  ONCE_UNTIL_SUCCESS,
}

enum class ToolStatus {
  SUCCESS,
  BLOCKED,
  FAILED,
}

enum class TaskStatus {
  PENDING,
  RUNNING,
  WAITING_FOR_APPROVAL,
  COMPLETED,
  FAILED,
}

enum class ApprovalDecision {
  ALLOW_ONCE,
  DENY,
  TIMEOUT,
}

data class ToolPrecondition(
  val kind: ToolPreconditionKind,
  val key: String? = null,
  val description: String,
)

data class ToolDefinition(
  val name: String,
  val description: String,
  val category: ToolCategory,
  val riskLevel: RiskLevel,
  val capabilityGroup: String,
  val requiresForeground: Boolean = false,
  val requiresConfirmation: Boolean = false,
  val cooldownMs: Long = 0,
  val timeoutMs: Long = 15_000,
  val preconditions: List<ToolPrecondition> = emptyList(),
  val inputSchemaHint: String? = null,
  val outputSchemaHint: String? = null,
  val idempotencyPolicy: ToolIdempotencyPolicy = ToolIdempotencyPolicy.PER_REQUEST,
)

data class ToolRequest(
  val requestId: String,
  val idempotencyKey: String,
  val toolName: String,
  val args: Map<String, Any?> = emptyMap(),
  val sessionId: String,
  val taskId: String,
)

data class Observation(
  val source: String,
  val kind: String,
  val summary: String,
  val confidence: Double = 1.0,
  val timestampMs: Long = System.currentTimeMillis(),
  val payload: Map<String, Any?> = emptyMap(),
)

data class ToolResult(
  val requestId: String,
  val toolName: String,
  val status: ToolStatus,
  val message: String,
  val payload: Map<String, Any?> = emptyMap(),
  val observations: List<Observation> = emptyList(),
  val requiresUserAction: Boolean = false,
  val retryable: Boolean = false,
)

data class ApprovalRequest(
  val approvalId: String,
  val taskId: String,
  val toolName: String,
  val riskLevel: RiskLevel,
  val summary: String,
)

data class PendingApproval(
  val approvalId: String,
  val taskId: String,
  val toolName: String,
  val riskLevel: RiskLevel,
  val summary: String,
  val createdAtMs: Long,
  val expiresAtMs: Long,
  val decision: ApprovalDecision? = null,
)

data class WorkflowStepState(
  val stepId: String,
  val toolName: String,
  val status: String,
)

data class TaskLogEvent(
  val taskId: String,
  val type: String,
  val message: String,
  val payload: Map<String, Any?> = emptyMap(),
)

data class TaskContext(
  val taskId: String,
  val sessionId: String,
  val goal: String,
  val status: TaskStatus,
  val currentStepIndex: Int = 0,
  val pendingApprovalId: String? = null,
  val activeRecipeId: String? = null,
  val workflowSteps: List<WorkflowStepState> = emptyList(),
  val observations: List<Observation> = emptyList(),
)
