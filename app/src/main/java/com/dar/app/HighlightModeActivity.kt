package com.dar.app

import android.app.Activity
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

                val cardsData = sortedDates.map { date ->
                    val rows = db.recordingDao().getRowsForDate(dslaId, date).sortedBy { it.rowNumber }
                    val firstFive = rows.take(5).mapNotNull { it.actionName.ifBlank { null } }
                    HighlightCardData(date, firstFive)
                }

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
                val resultIntent = Intent().apply {
                    putExtra(RecordingActivity.RESULT_EXTRA_DATE, data.date)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }

            container.addView(cardView)
        }
    }
}