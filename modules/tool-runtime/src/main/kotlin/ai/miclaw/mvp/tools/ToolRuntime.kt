package ai.miclaw.mvp.tools

import ai.miclaw.mvp.models.ToolDefinition
import ai.miclaw.mvp.models.ToolPreconditionKind
import ai.miclaw.mvp.models.ToolRequest
import ai.miclaw.mvp.models.ToolResult
import ai.miclaw.mvp.models.ToolStatus
import ai.miclaw.mvp.models.Observation

interface ToolHandler {
  val definition: ToolDefinition

  suspend fun handle(request: ToolRequest, context: ToolExecutionContext): ToolResult
}

data class ToolExecutionContext(
  val sessionId: String,
  val taskId: String,
  val isForeground: Boolean = false,
  val grantedPermissions: Set<String> = emptySet(),
  val enabledServices: Set<String> = emptySet(),
  val metadata: Map<String, String> = emptyMap(),
)

interface ToolInterceptor {
  suspend fun before(request: ToolRequest, definition: ToolDefinition, context: ToolExecutionContext) {}

  suspend fun after(
    request: ToolRequest,
    definition: ToolDefinition,
    context: ToolExecutionContext,
    result: ToolResult,
  ) {
  }
}

class ToolRegistry(
  handlers: List<ToolHandler> = emptyList(),
) {
  private val handlersByName = handlers.associateBy { it.definition.name }.toMutableMap()

  fun register(handler: ToolHandler) {
    handlersByName[handler.definition.name] = handler
  }

  fun definitions(): List<ToolDefinition> = handlersByName.values.map { it.definition }.sortedBy { it.name }

  fun resolve(toolName: String): ToolHandler? = handlersByName[toolName]
}

class ToolDispatcher(
  private val registry: ToolRegistry,
  private val interceptors: List<ToolInterceptor> = emptyList(),
) {
  suspend fun dispatch(request: ToolRequest, context: ToolExecutionContext): ToolResult {
    val handler = registry.resolve(request.toolName)
      ?: error("No handler registered for tool ${request.toolName}")
    val definition = handler.definition
    val preconditionFailure = evaluatePreconditions(definition, context, request)
    if (preconditionFailure != null) {
      return preconditionFailure
    }
    interceptors.forEach { it.before(request, definition, context) }
    val result = handler.handle(request, context)
    interceptors.forEach { it.after(request, definition, context, result) }
    return result
  }

  private fun evaluatePreconditions(
    definition: ToolDefinition,
    context: ToolExecutionContext,
    request: ToolRequest,
  ): ToolResult? {
    if (definition.requiresForeground && !context.isForeground) {
      return blockedResult(
        request = request,
        source = "tool-runtime",
        message = "Tool ${definition.name} requires foreground execution.",
      )
    }
    for (precondition in definition.preconditions) {
      when (precondition.kind) {
        ToolPreconditionKind.FOREGROUND -> {
          if (!context.isForeground) {
            return blockedResult(request, "tool-runtime", precondition.description)
          }
        }
        ToolPreconditionKind.PERMISSION_GRANTED -> {
          if (precondition.key.isNullOrBlank() || precondition.key !in context.grantedPermissions) {
            return blockedResult(request, "tool-runtime", precondition.description)
          }
        }
        ToolPreconditionKind.SERVICE_ENABLED -> {
          if (precondition.key.isNullOrBlank() || precondition.key !in context.enabledServices) {
            return blockedResult(request, "tool-runtime", precondition.description)
          }
        }
        ToolPreconditionKind.CONNECTOR_READY -> {
          val metadataKey = precondition.key
          val ready = metadataKey != null && context.metadata[metadataKey].equals("true", ignoreCase = true)
          if (!ready) {
            return blockedResult(request, "tool-runtime", precondition.description)
          }
        }
      }
    }
    return null
  }

  private fun blockedResult(
    request: ToolRequest,
    source: String,
    message: String,
  ): ToolResult {
    return ToolResult(
      requestId = request.requestId,
      toolName = request.toolName,
      status = ToolStatus.BLOCKED,
      message = message,
      observations = listOf(Observation(source = source, kind = "precondition", summary = message)),
      requiresUserAction = false,
      retryable = false,
    )
  }
}
