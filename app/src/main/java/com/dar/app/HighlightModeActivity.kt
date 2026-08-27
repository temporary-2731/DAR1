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

private data class HighlightCardData(val date: String, val firstFiveActions: List<String>)

class HighlightModeActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L
    private lateinit var container: LinearLayout

    // Guards against the duplicate-card bug: if the underlying data changes while a
    // previous render is still gathering data, only the newest render is allowed to
    // actually draw — stale results are discarded instead of being appended alongside.
    private var renderGeneration = 0

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
                renderGeneration++
                val myGeneration = renderGeneration

                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val sortedDates = dates.sortedByDescending { sdf.parse(it)?.time ?: 0L }

                // Gather every card's data fully before touching the UI at all.
                val cardsData = sortedDates.map { date ->
                    val rows = db.recordingDao().getRowsForDate(dslaId, date).sortedBy { it.rowNumber }
                    val firstFive = rows.take(5).mapNotNull { it.actionName.ifBlank { null } }
                    HighlightCardData(date, firstFive)
                }

                // If a newer emission arrived while we were gathering, drop this stale result.
                if (myGeneration != renderGeneration) return@collect

                renderAllCards(cardsData)
            }
        }
    }

    private fun renderAllCards(cardsData: List<HighlightCardData>) {
        container.removeAllViews()
        for (data in cardsData) {
            val cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_highlight_day_card, container, false)
            val dateText = cardView.findViewById<TextView>(R.id.highlight_date_text)
            val actionsText = cardView.findViewById<TextView>(R.id.highlight_actions_text)

            dateText.text = data.date
            actionsText.text = if (data.firstFiveActions.isEmpty()) {
                getString(R.string.highlight_no_actions)
            } else {
                data.firstFiveActions.joinToString(", ")
            }

            cardView.setOnClickListener {
                val intent = Intent(this, RecordingActivity::class.java).apply {
                    putExtra(RecordingActivity.EXTRA_DSLA_ID, dslaId)
                    putExtra(RecordingActivity.EXTRA_MODE, "HISTORY")
                    putExtra(RecordingActivity.EXTRA_TARGET_DATE, data.date)
                }
                startActivity(intent)
            }

            container.addView(cardView)
        }
    }
}