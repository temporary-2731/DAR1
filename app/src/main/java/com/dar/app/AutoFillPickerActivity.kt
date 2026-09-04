package com.dar.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AnalysisForm
import com.dar.app.data.AnalysisFormActionParam
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lists every Daily/Weekly form across the DSLA, with 7 weekday buttons each. Tapping a
 * weekday copies every parameter row whose Action also belongs to the TARGET form's
 * General Action into the target form/weekday — matched by Action identity, which stays
 * meaningful across different General Actions since one Action can belong to several.
 */
class AutoFillPickerActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var targetFormId: Long = -1L
    private var targetWeekday: Int = 1
    private var targetGeneralActionId: Long = -1L
    private lateinit var container: LinearLayout

    companion object {
        const val EXTRA_TARGET_FORM_ID = "extra_target_form_id"
        const val EXTRA_TARGET_WEEKDAY = "extra_target_weekday"
        const val EXTRA_TARGET_GENERAL_ACTION_ID = "extra_target_general_action_id"
        const val RESULT_COPIED_COUNT = "result_copied_count"

        private val weekdayShort = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_fill_picker)

        targetFormId = intent.getLongExtra(EXTRA_TARGET_FORM_ID, -1L)
        targetWeekday = intent.getIntExtra(EXTRA_TARGET_WEEKDAY, 1)
        targetGeneralActionId = intent.getLongExtra(EXTRA_TARGET_GENERAL_ACTION_ID, -1L)
        db = AppDatabase.getInstance(applicationContext)

        container = findViewById(R.id.auto_fill_list_container)
        loadForms()
    }

    private fun loadForms() {
        lifecycleScope.launch {
            val targetFormRecord = db.analysisFormDao().getFormById(targetFormId)
            if (targetFormRecord == null) {
                Toast.makeText(this@AutoFillPickerActivity, "Form not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val allForms = db.analysisFormDao().getDailyAndWeeklyFormsForDsla(targetFormRecord.dslaId)
            val generalActions = db.generalActionDao().getAllForDsla(targetFormRecord.dslaId).first()

            container.removeAllViews()
            var anyShown = false
            for (form in allForms) {
                if (form.id == targetFormId) continue
                val generalName = generalActions.firstOrNull { it.id == form.generalActionId }?.name ?: "GA#${form.generalActionId}"
                renderFormEntry(form, generalName)
                anyShown = true
            }

            if (!anyShown) {
                val empty = TextView(this@AutoFillPickerActivity)
                empty.text = getString(R.string.auto_fill_no_other_forms)
                empty.setTextColor(android.graphics.Color.DKGRAY)
                container.addView(empty)
            }
        }
    }

    private fun renderFormEntry(form: AnalysisForm, generalName: String) {
        val entryView = LayoutInflater.from(this).inflate(R.layout.item_auto_fill_form_entry, container, false)
        val title = entryView.findViewById<TextView>(R.id.auto_fill_entry_title)
        val row1 = entryView.findViewById<LinearLayout>(R.id.auto_fill_weekday_row_1)
        val row2 = entryView.findViewById<LinearLayout>(R.id.auto_fill_weekday_row_2)

        val rangeText = if (form.endDate != null) "${form.beginDate} — ${form.endDate}" else "${form.beginDate} — ongoing"
        title.text = "$generalName — ${form.periodType} — ($rangeText)"

        for (weekday in 1..7) {
            val btn = Button(this)
            btn.text = weekdayShort[weekday - 1]
            btn.textSize = 11f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.marginEnd = 4
            btn.layoutParams = params
            btn.setOnClickListener { performCopy(form.id, weekday) }
            if (weekday <= 4) row1.addView(btn) else row2.addView(btn)
        }

        container.addView(entryView)
    }

    private fun performCopy(sourceFormId: Long, sourceWeekday: Int) {
        lifecycleScope.launch {
            val targetActions = db.generalActionDao().getActionsInGeneral(targetGeneralActionId).first()
            val targetActionIds = targetActions.map { it.id }.toSet()

            val sourceParams = db.analysisFormDao().getParamsForFormWeekdayOnce(sourceFormId, sourceWeekday)
            val existingTargetParams = db.analysisFormDao().getParamsForFormWeekdayOnce(targetFormId, targetWeekday)

            if (sourceParams.isEmpty()) {
                Toast.makeText(this@AutoFillPickerActivity, R.string.auto_fill_source_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }

            var copiedCount = 0
            for (sourceParam in sourceParams) {
                if (sourceParam.actionId !in targetActionIds) continue

                val existing = existingTargetParams.firstOrNull { it.actionId == sourceParam.actionId }
                if (existing == null) {
                    db.analysisFormDao().insertParam(
                        AnalysisFormActionParam(
                            formId = targetFormId,
                            actionId = sourceParam.actionId,
                            weekday = targetWeekday,
                            dimension = sourceParam.dimension,
                            timeVector = sourceParam.timeVector,
                            durationVector = sourceParam.durationVector,
                            quan1Vector = sourceParam.quan1Vector
                        )
                    )
                } else {
                    db.analysisFormDao().updateParam(
                        existing.copy(
                            dimension = sourceParam.dimension,
                            timeVector = sourceParam.timeVector,
                            durationVector = sourceParam.durationVector,
                            quan1Vector = sourceParam.quan1Vector
                        )
                    )
                }
                copiedCount++
            }

            val resultIntent = Intent().apply {
                putExtra(RESULT_COPIED_COUNT, copiedCount)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}