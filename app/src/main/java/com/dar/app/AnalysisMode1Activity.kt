package com.dar.app

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.launch

class AnalysisMode1Activity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L
    private lateinit var container: LinearLayout

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_mode1)

        dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        db = AppDatabase.getInstance(applicationContext)
        container = findViewById(R.id.mode1_list_container)

        loadItems()
    }

    private fun loadItems() {
        lifecycleScope.launch {
            val generals = db.generalActionDao().getActiveForDsla(dslaId).first0()
            val supers = db.superActionDao().getActiveForDsla(dslaId).first0()

            if (generals.isEmpty() && supers.isEmpty()) {
                val empty = TextView(this@AnalysisMode1Activity)
                empty.text = getString(R.string.analysis_mode1_no_items)
                empty.setTextColor(android.graphics.Color.DKGRAY)
                container.addView(empty)
                return@launch
            }

            for (g in generals) {
                addItem(g.name)
            }
            for (s in supers) {
                addItem(s.name)
            }
        }
    }

    private fun addItem(label: String) {
        val itemView = LayoutInflater.from(this)
            .inflate(R.layout.item_action, container, false) as TextView
        itemView.text = label
        itemView.setOnClickListener {
            Toast.makeText(this, R.string.analysis_coming_soon, Toast.LENGTH_SHORT).show()
        }
        container.addView(itemView)
    }

    // Small local helper since these DAOs return Flow — take the first emission only.
    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.first0(): T {
        return kotlinx.coroutines.flow.first(this)
    }
}
