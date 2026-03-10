package ai.miclaw.mvp.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val runtime = MvpRuntime.get(applicationContext)
    val toolSummary = runtime.registry
      .definitions()
      .joinToString(separator = "\n") { "- ${it.name} (${it.category}, ${it.riskLevel})" }

    val text = TextView(this).apply {
      text =
        """
        Android miclaw MVP skeleton

        Registered tools:
        $toolSummary

        Core docs:
        - docs/tool-contract.md
        - docs/risk-policy.md
        - docs/demo-scenarios.md
        - docs/multi-agent-workflow.md
        """.trimIndent()
      textSize = 16f
      setPadding(32, 48, 32, 48)
    }

    setContentView(text)
  }
}
