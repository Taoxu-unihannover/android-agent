package ai.miclaw.mvp.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class AgentForegroundService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null
}
