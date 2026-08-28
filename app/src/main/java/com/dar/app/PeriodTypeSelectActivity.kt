package com.dar.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PeriodTypeSelectActivity : AppCompatActivity() {

    private var generalActionId: Long = -1L
    private var dslaId: Long = -1L

    companion object {
        const val EXTRA_GENERAL_ACTION_ID = "extra_general_action_id"
        const val EXTRA_DSLA_ID = "extra_dsla_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_period_type_select)

        generalActionId = intent.getLongExtra(EXTRA_GENERAL_ACTION_ID, -1L)
        dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)

        findViewById<android.widget.Button>(R.id.btn_period_daily).setOnClickListener { openForms("DAILY") }
        findViewById<android.widget.Button>(R.id.btn_period_weekly).setOnClickListener { openForms("WEEKLY") }
        findViewById<android.widget.Button>(R.id.btn_period_monthly).setOnClickListener { openForms("MONTHLY") }
        findViewById<android.widget.Button>(R.id.btn_period_yearly).setOnClickListener { openForms("YEARLY") }
        findViewById<android.widget.Button>(R.id.btn_period_alltime).setOnClickListener { openForms("ALLTIME") }
    }

    private fun openForms(periodType: String) {
        val intent = Intent(this, FormListActivity::class.java).apply {
            putExtra(FormListActivity.EXTRA_DSLA_ID, dslaId)
            putExtra(FormListActivity.EXTRA_GENERAL_ACTION_ID, generalActionId)
            putExtra(FormListActivity.EXTRA_PERIOD_TYPE, periodType)
        }
        startActivity(intent)
    }
}
