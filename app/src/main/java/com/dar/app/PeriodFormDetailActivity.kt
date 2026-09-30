package com.dar.app

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.ActionEntity
import com.dar.app.data.AnalysisForm
import com.dar.app.data.AnalysisFormScalarParam
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Parameter entry for Monthly/Yearly/All-time forms: single Duration + Quan1 scalar per
 *  Action (Time ignored). Auto-calculate sums the covering Daily/Weekly form's parameter
 *  vector total for every day in this form's range. Gaps (days with no covering form or
 *  parameters) are listed as plain text — you can adjust the resulting total by hand to
 *  account for them; the interactive per-gap "extend" picker is deferred to a later round. */
class PeriodFormDetailActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var formId: Long = -1L
    private var generalActionId: Long = -1L
    private lateinit var currentForm: AnalysisForm

    private lateinit var container: LinearLayout
    private lateinit var gapTitle: TextView
    private lateinit var gapText: TextView

    private data class RowRefs(val action: ActionEntity, val durationField: EditText, val quan1Field: EditText)
    private val rowRefs = mutableListOf<RowRefs>()

    companion object {
        const val EXTRA_FORM_ID = "extra_form_id"
        const val EXTRA_GENERAL_ACTION_ID = "extra_general_action_id"
        private const val DATE_FORMAT = "dd/MM/yyyy"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_period_form_detail)

        formId = intent.getLongExtra(EXTRA_FORM_ID, -1L)
        generalActionId = intent.getLongExtra(EXTRA_GENERAL_ACTION_ID, -1L)
        db = AppDatabase.getInstance(applicationContext)

        container = findViewById(R.id.scalar_rows_container)
        gapTitle = findViewById(R.id.gap_report_title)
        gapText = findViewById(R.id.gap_report_text)

        findViewById<Button>(R.id.btn_period_save).setOnClickListener { attemptSave() }
        findViewById<Button>(R.id.btn_auto_calculate).setOnClickListener { runAutoCalculate() }

        loadRows()
    }

    private fun loadRows() {
        lifecycleScope.launch {
            val form = db.analysisFormDao().getFormById(formId) ?: return@launch
            currentForm = form
            findViewById<TextView>(R.id.period_form_title).text =
                "${form.periodType} — ${form.beginDate}${if (form.endDate != null) " to ${form.endDate}" else " ongoing"}"

            val actions = db.generalActionDao().getActionsInGeneral(generalActionId).first()
            val existingParams = db.analysisFormDao().getScalarParamsForForm(formId).first()

            container.removeAllViews()
            rowRefs.clear()
            for (action in actions) {
                val existing = existingParams.firstOrNull { it.actionId == action.id }
                addRow(action, existing)
            }
        }
    }

    private fun addRow(action: ActionEntity, existing: AnalysisFormScalarParam?) {
        val rowView = LayoutInflater.from(this).inflate(R.layout.item_scalar_param_row, container, false)
        val nameText = rowView.findViewById<TextView>(R.id.scalar_row_action_name)
        val durationField = rowView.findViewById<EditText>(R.id.edit_scalar_duration)
        val quan1Field = rowView.findViewById<EditText>(R.id.edit_scalar_quan1)

        nameText.text = action.name
        durationField.setText(existing?.durationValue?.toString() ?: "")
        quan1Field.setText(existing?.quan1Value?.toString() ?: "")

        rowRefs.add(RowRefs(action, durationField, quan1Field))
        container.addView(rowView)
    }

    private fun attemptSave() {
        lifecycleScope.launch {
            val existingParams = db.analysisFormDao().getScalarParamsForFormOnce(formId)
            for (row in rowRefs) {
                val duration = row.durationField.text.toString().trim().toDoubleOrNull()
                val quan1 = row.quan1Field.text.toString().trim().toDoubleOrNull()
                val existing = existingParams.firstOrNull { it.actionId == row.action.id }
                if (existing == null) {
                    db.analysisFormDao().insertScalarParam(
                        AnalysisFormScalarParam(formId = formId, actionId = row.action.id, durationValue = duration, quan1Value = quan1)
                    )
                } else {
                    db.analysisFormDao().updateScalarParam(existing.copy(durationValue = duration, quan1Value = quan1))
                }
            }
            Toast.makeText(this@PeriodFormDetailActivity, R.string.param_save, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * For each day in this form's range: finds the covering Daily form (preferred) or
     * Weekly form for that weekday, sums that form's parameter vector for the Action
     * (the "dot product with (1,1,...,1)" you described), and accumulates into this
     * form's scalar Duration/Quan1. Days with no coverage are listed as gaps.
     */
    private fun runAutoCalculate() {
        lifecycleScope.launch {
            val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            val start = dateFormat.parse(currentForm.beginDate) ?: return@launch
            val end = currentForm.endDate?.let { dateFormat.parse(it) } ?: Date()

            val dailyForms = db.analysisFormDao().getFormsForOnce(generalActionId, "DAILY")
            val weeklyForms = db.analysisFormDao().getFormsForOnce(generalActionId, "WEEKLY")

            val dailyParamsByForm = dailyForms.associateWith { db.analysisFormDao().getParamsForFormOnce(it.id) }
            val weeklyParamsByForm = weeklyForms.associateWith { db.analysisFormDao().getParamsForFormOnce(it.id) }

            val gapDates = mutableListOf<String>()

            for (row in rowRefs) {
                var durationSum = 0.0
                var quan1Sum = 0.0
                var anyCoverage = false

                val cal = Calendar.getInstance()
                cal.time = start
                while (!cal.time.after(end)) {
                    val date = cal.time
                    val dateStr = dateFormat.format(date)
                    val weekday = cal.get(Calendar.DAY_OF_WEEK)

                    val coveringDaily = dailyForms.firstOrNull { f ->
                        val b = dateFormat.parse(f.beginDate); val e = f.endDate?.let { dateFormat.parse(it) }
                        b != null && !date.before(b) && (e == null || !date.after(e))
                    }
                    val coveringWeekly = weeklyForms.firstOrNull { f ->
                        val b = dateFormat.parse(f.beginDate); val e = f.endDate?.let { dateFormat.parse(it) }
                        b != null && !date.before(b) && (e == null || !date.after(e))
                    }

                    val param = coveringDaily?.let { f -> dailyParamsByForm[f]?.firstOrNull { it.actionId == row.action.id && it.weekday == weekday } }
                        ?: coveringWeekly?.let { f -> weeklyParamsByForm[f]?.firstOrNull { it.actionId == row.action.id && it.weekday == weekday } }

                    if (param == null) {
                        gapDates.add("${row.action.name}: $dateStr")
                    } else {
                        anyCoverage = true
                        durationSum += sumVector(param.durationVector)
                        quan1Sum += sumVector(param.quan1Vector)
                    }

                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }

                if (anyCoverage) {
                    row.durationField.setText(String.format(Locale.getDefault(), "%.2f", durationSum))
                    row.quan1Field.setText(String.format(Locale.getDefault(), "%.2f", quan1Sum))
                }
            }

            if (gapDates.isNotEmpty()) {
                gapTitle.visibility = android.view.View.VISIBLE
                gapText.text = gapDates.joinToString("\n")
            } else {
                gapTitle.visibility = android.view.View.GONE
                gapText.text = ""
            }

            Toast.makeText(this@PeriodFormDetailActivity, R.string.period_auto_calculate_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sumVector(csv: String): Double {
        if (csv.isBlank()) return 0.0
        return csv.split(",").mapNotNull { it.trim().toDoubleOrNull() }.sum()
    }
}
