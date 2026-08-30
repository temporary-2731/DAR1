package com.dar.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.launch
import java.util.Locale

class EngineCheckActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
        const val EXTRA_GENERAL_ACTION_ID = "extra_general_action_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_engine_check)

        val dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        val generalActionId = intent.getLongExtra(EXTRA_GENERAL_ACTION_ID, -1L)
        val logView = findViewById<TextView>(R.id.engine_check_log)
        val resultsView = findViewById<TextView>(R.id.engine_check_results)

        logView.text = getString(R.string.check_engine_running)
        resultsView.text = ""

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val engine = DailyAnalysisEngine(db)
            engine.debugEnabled = true

            val results = engine.computeAllTime(dslaId, generalActionId)

            logView.text = engine.debugLog.toString().ifEmpty { getString(R.string.check_engine_no_forms) }

            val builder = StringBuilder()
            builder.appendLine("=== FINAL RESULTS (${results.size} period/weekday groups) ===\n")
            for (result in results) {
                builder.appendLine("${result.label} — ${result.weekdayName}")
                for (action in result.actionStats) {
                    builder.appendLine("  [${action.actionName}] occurrences=${action.time.occurrenceCount}")
                    builder.appendLine(metricBlock("    TIME", action.time))
                    builder.appendLine(metricBlock("    DURATION", action.duration))
                    builder.appendLine(metricBlock("    QUAN1", action.quan1))
                }
                builder.appendLine("  GENERAL:")
                builder.appendLine(metricBlock("    TIME", result.generalTime))
                builder.appendLine(metricBlock("    DURATION", result.generalDuration))
                builder.appendLine(metricBlock("    QUAN1", result.generalQuan1))
                builder.appendLine()
            }
            resultsView.text = builder.toString()
        }
    }

    private fun metricBlock(label: String, m: MetricAggregate): String {
        val sb = StringBuilder()
        sb.append("$label: n=${m.occurrenceCount} ")
        sb.append("avgDist=${format(m.avgDistance)} ")
        sb.append("avgVar=${format(m.avgVariation)} ")
        sb.append("avgRate=${formatPct(m.avgRate)}")
        if (m.totSum != null) {
            sb.append(" | totSum=${format(m.totSum)} avgTot=${format(m.avgTot)} avgParamTot=${format(m.avgParamTotal)} compAvgTot=${formatPct(m.compAvgTot)}")
        }
        return sb.toString()
    }

    private fun format(value: Double?): String {
        return if (value == null) "—" else String.format(Locale.getDefault(), "%.2f", value)
    }

    private fun formatPct(value: Double?): String {
        return if (value == null) "—" else String.format(Locale.getDefault(), "%.2f%%", value)
    }
}