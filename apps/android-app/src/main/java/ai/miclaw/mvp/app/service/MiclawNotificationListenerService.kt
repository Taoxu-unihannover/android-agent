package ai.miclaw.mvp.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ai.miclaw.mvp.app.runtime.NotificationEventStore
import ai.miclaw.mvp.app.runtime.NotificationRecord

class MiclawNotificationListenerService : NotificationListenerService() {
  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    super.onNotificationPosted(sbn)
    val notification = sbn?.notification ?: return
    NotificationEventStore.add(
      NotificationRecord(
        packageName = sbn.packageName.orEmpty(),
        title = notification.extras?.getCharSequence("android.title")?.toString(),
        text = notification.extras?.getCharSequence("android.text")?.toString(),
        postedAtMs = sbn.postTime,
      ),
    )
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    super.onNotificationRemoved(sbn)
  }
}
