package ai.miclaw.mvp.app.runtime

data class NotificationRecord(
  val packageName: String,
  val title: String?,
  val text: String?,
  val postedAtMs: Long,
)

object NotificationEventStore {
  private val records = mutableListOf<NotificationRecord>()

  @Synchronized
  fun add(record: NotificationRecord) {
    records.add(0, record)
    if (records.size > 100) {
      records.removeAt(records.lastIndex)
    }
  }

  @Synchronized
  fun list(): List<NotificationRecord> = records.toList()
}
