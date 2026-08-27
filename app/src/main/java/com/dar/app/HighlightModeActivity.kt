package com.dar.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class HighlightModeActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L
    private lateinit var container: LinearLayout

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_highlight_mode)

        dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        db = AppDatabase.getInstance(applicationContext)
        container = findViewById(R.id.highlight_list_container)

        loadDays()
    }

    private fun loadDays() {
        lifecycleScope.launch {
            db.recordingDao().getDistinctDates(dslaId).collect { dates ->
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val sorted = dates.sortedByDescending { sdf.parse(it)?.time ?: 0L }
                renderDays(sorted)
            }
        }
    }

    private fun renderDays(dates: List<String>) {
        container.removeAllViews()
        for (date in dates) {
            lifecycleScope.launch {
                val rows = db.recordingDao().getRowsForDate(dslaId, date).sortedBy { it.rowNumber }
                val firstFive = rows.take(5).mapNotNull { it.actionName.ifBlank { null } }

                val cardView = LayoutInflater.from(this@HighlightModeActivity)
                    .inflate(R.layout.item_highlight_day_card, container, false)
                val dateText = cardView.findViewById<TextView>(R.id.highlight_date_text)
                val actionsText = cardView.findViewById<TextView>(R.id.highlight_actions_text)

                dateText.text = date
                actionsText.text = if (firstFive.isEmpty()) {
                    getString(R.string.highlight_no_actions)
                } else {
                    firstFive.joinToString(", ")
                }

                cardView.setOnClickListener {
                    val intent = Intent(this@HighlightModeActivity, RecordingActivity::class.java).apply {
                        putExtra(RecordingActivity.EXTRA_DSLA_ID, dslaId)
                        putExtra(RecordingActivity.EXTRA_MODE, "HISTORY")
                        putExtra(RecordingActivity.EXTRA_TARGET_DATE, date)
                    }
                    startActivity(intent)
                }

                container.addView(cardView)
            }
        }
    }
}
