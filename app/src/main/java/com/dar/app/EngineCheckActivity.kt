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
            builder.appendLine("=== FINAL RESULTS (${results.size} season/weekday groups) ===\n")
            for (result in results) {
                builder.appendLine("${result.seasonLabel} — ${result.weekdayName}")
                for (action in result.actionStats) {
                    if (action.occurrenceCount == 0) continue
                    builder.appendLine("  ${action.actionName}: occurrences=${action.occurrenceCount}")
                    builder.appendLine("    Time distance=${format(action.avgDistanceTime)} variation=${format(action.avgVariationTime)}")
                    builder.appendLine("    Duration total=${format(action.totalDurationSum)} distance=${format(action.avgDistanceDuration)} variation=${format(action.avgVariationDuration)} pct=${format(action.durationPercentage)}")
                    builder.appendLine("    Quan1 total=${format(action.totalQuan1Sum)} distance=${format(action.avgDistanceQuan1)} variation=${format(action.avgVariationQuan1)} pct=${format(action.quan1Percentage)}")
                }
                builder.appendLine("  GENERAL: duration total=${format(result.generalDurationTotal)} avgVariation=${format(result.generalDurationAvgVariation)}")
                builder.appendLine("  GENERAL: quan1 total=${format(result.generalQuan1Total)} avgVariation=${format(result.generalQuan1AvgVariation)}")
                builder.appendLine()
            }
            resultsView.text = builder.toString()
        }
    }

    private fun format(value: Double?): String {
        return if (value == null) "—" else String.format(Locale.getDefault(), "%.2f", value)
    }
}