package ai.miclaw.mvp.policy

import ai.miclaw.mvp.models.RiskLevel
import ai.miclaw.mvp.models.ToolDefinition

data class PolicyDecision(
  val allowed: Boolean,
  val requiresApproval: Boolean,
  val reason: String,
)

class RiskPolicyEngine {
  fun evaluate(definition: ToolDefinition): PolicyDecision {
    return when (definition.riskLevel) {
      RiskLevel.L0 -> PolicyDecision(allowed = true, requiresApproval = false, reason = "read-only")
      RiskLevel.L1 -> PolicyDecision(allowed = true, requiresApproval = false, reason = "low-risk")
      RiskLevel.L2 -> PolicyDecision(
        allowed = true,
        requiresApproval = definition.requiresConfirmation,
        reason = "medium-risk",
      )
      RiskLevel.L3 -> PolicyDecision(
        allowed = false,
        requiresApproval = true,
        reason = "not enabled in mvp",
      )
    }
  }
}
