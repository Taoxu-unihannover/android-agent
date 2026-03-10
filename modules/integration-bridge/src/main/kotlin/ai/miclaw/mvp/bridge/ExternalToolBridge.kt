package ai.miclaw.mvp.bridge

import ai.miclaw.mvp.models.Observation

data class ExternalBridgeRequest(
  val name: String,
  val payload: Map<String, Any?>,
)

data class ExternalBridgeResponse(
  val ok: Boolean,
  val message: String,
  val observations: List<Observation> = emptyList(),
)

interface ExternalToolBridge {
  suspend fun invoke(request: ExternalBridgeRequest): ExternalBridgeResponse
}
