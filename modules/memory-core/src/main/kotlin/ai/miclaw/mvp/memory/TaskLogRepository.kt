package ai.miclaw.mvp.memory

import ai.miclaw.mvp.models.TaskLogEvent

interface TaskLogRepository {
  fun append(event: TaskLogEvent)

  fun list(taskId: String): List<TaskLogEvent>
}

class InMemoryTaskLogRepository : TaskLogRepository {
  private val events = mutableListOf<TaskLogEvent>()

  override fun append(event: TaskLogEvent) {
    events += event
  }

  override fun list(taskId: String): List<TaskLogEvent> {
    return events.filter { it.taskId == taskId }
  }
}
