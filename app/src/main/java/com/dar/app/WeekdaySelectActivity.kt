package com.dar.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class WeekdaySelectActivity : AppCompatActivity() {

    private var formId: Long = -1L
    private var generalActionId: Long = -1L

    companion object {
        const val EXTRA_FORM_ID = "extra_form_id"
        const val EXTRA_GENERAL_ACTION_ID = "extra_general_action_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekday_select)

        formId = intent.getLongExtra(EXTRA_FORM_ID, -1L)
        generalActionId = intent.getLongExtra(EXTRA_GENERAL_ACTION_ID, -1L)

        findViewById<Button>(R.id.btn_weekday_sunday).setOnClickListener { openDetail(1) }
        findViewById<Button>(R.id.btn_weekday_monday).setOnClickListener { openDetail(2) }
        findViewById<Button>(R.id.btn_weekday_tuesday).setOnClickListener { openDetail(3) }
        findViewById<Button>(R.id.btn_weekday_wednesday).setOnClickListener { openDetail(4) }
        findViewById<Button>(R.id.btn_weekday_thursday).setOnClickListener { openDetail(5) }
        findViewById<Button>(R.id.btn_weekday_friday).setOnClickListener { openDetail(6) }
        findViewById<Button>(R.id.btn_weekday_saturday).setOnClickListener { openDetail(7) }
    }

    private fun openDetail(weekday: Int) {
        val intent = Intent(this, FormDetailActivity::class.java).apply {
            putExtra(FormDetailActivity.EXTRA_FORM_ID, formId)
            putExtra(FormDetailActivity.EXTRA_GENERAL_ACTION_ID, generalActionId)
            putExtra(FormDetailActivity.EXTRA_WEEKDAY, weekday)
        }
        startActivity(intent)
    }
}
