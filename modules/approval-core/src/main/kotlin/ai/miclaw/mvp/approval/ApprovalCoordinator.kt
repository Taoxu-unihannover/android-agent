package ai.miclaw.mvp.approval

import ai.miclaw.mvp.models.ApprovalDecision
import ai.miclaw.mvp.models.PendingApproval
import ai.miclaw.mvp.models.RiskLevel
import java.util.UUID

interface ApprovalStore {
  fun create(taskId: String, toolName: String, riskLevel: RiskLevel, summary: String, ttlMs: Long): PendingApproval

  fun getPending(taskId: String): PendingApproval?

  fun resolve(approvalId: String, decision: ApprovalDecision): PendingApproval?

  fun expire(nowMs: Long = System.currentTimeMillis()): List<PendingApproval>
}

class InMemoryApprovalStore : ApprovalStore {
  private val approvals = linkedMapOf<String, PendingApproval>()

  override fun create(
    taskId: String,
    toolName: String,
    riskLevel: RiskLevel,
    summary: String,
    ttlMs: Long,
  ): PendingApproval {
    val approval = PendingApproval(
      approvalId = UUID.randomUUID().toString(),
      taskId = taskId,
      toolName = toolName,
      riskLevel = riskLevel,
      summary = summary,
      createdAtMs = System.currentTimeMillis(),
      expiresAtMs = System.currentTimeMillis() + ttlMs,
    )
    approvals[approval.approvalId] = approval
    return approval
  }

  override fun getPending(taskId: String): PendingApproval? {
    return approvals.values.firstOrNull { it.taskId == taskId && it.decision == null && it.expiresAtMs > System.currentTimeMillis() }
  }

  override fun resolve(approvalId: String, decision: ApprovalDecision): PendingApproval? {
    val approval = approvals[approvalId] ?: return null
    val resolved = approval.copy(decision = decision)
    approvals[approvalId] = resolved
    return resolved
  }

  override fun expire(nowMs: Long): List<PendingApproval> {
    val expired = approvals.values
      .filter { it.decision == null && it.expiresAtMs <= nowMs }
      .map { it.copy(decision = ApprovalDecision.TIMEOUT) }
    expired.forEach { approvals[it.approvalId] = it }
    return expired
  }
}

class ApprovalCoordinator(
  private val store: ApprovalStore,
  private val ttlMs: Long = 120_000,
) {
  fun request(taskId: String, toolName: String, riskLevel: RiskLevel, summary: String): PendingApproval {
    return store.create(taskId = taskId, toolName = toolName, riskLevel = riskLevel, summary = summary, ttlMs = ttlMs)
  }

  fun pending(taskId: String): PendingApproval? = store.getPending(taskId)

  fun resolve(approvalId: String, decision: ApprovalDecision): PendingApproval? = store.resolve(approvalId, decision)
}
