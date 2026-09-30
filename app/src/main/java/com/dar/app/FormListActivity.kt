package com.dar.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AnalysisForm
import com.dar.app.data.AnalysisFormActionParam
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FormListActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L
    private var generalActionId: Long = -1L
    private lateinit var periodType: String
    private lateinit var container: LinearLayout

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
        const val EXTRA_GENERAL_ACTION_ID = "extra_general_action_id"
        const val EXTRA_PERIOD_TYPE = "extra_period_type"
        private const val DATE_FORMAT = "dd/MM/yyyy"
        private val ALL_PERIOD_TYPES = listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY", "ALLTIME")
        private val VECTOR_TYPES = setOf("DAILY", "WEEKLY")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_list)

        dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        generalActionId = intent.getLongExtra(EXTRA_GENERAL_ACTION_ID, -1L)
        periodType = intent.getStringExtra(EXTRA_PERIOD_TYPE) ?: "DAILY"
        db = AppDatabase.getInstance(applicationContext)

        container = findViewById(R.id.form_list_container)
        findViewById<Button>(R.id.btn_create_form).setOnClickListener { showCreateFormDialog() }
        findViewById<Button>(R.id.btn_check_engine).setOnClickListener { openEngineCheck() }

        lifecycleScope.launch {
            ensureAllTimeFormExists()
            backfillFormShellsAndValues()
            observeForms()
        }
    }

    /** Every General Action gets exactly one ALLTIME form automatically, matching the
     *  DSLA's own begin/end date range. */
    private suspend fun ensureAllTimeFormExists() {
        val dsla = db.dslaDao().getById(dslaId) ?: return
        val existing = db.analysisFormDao().getFormsForOnce(generalActionId, "ALLTIME")
        if (existing.isEmpty()) {
            db.analysisFormDao().insertForm(
                AnalysisForm(
                    dslaId = dslaId,
                    generalActionId = generalActionId,
                    periodType = "ALLTIME",
                    beginDate = dsla.beginDate,
                    endDate = dsla.endDate
                )
            )
        }
    }

    /** Ensures every form of any of the 5 types has a same-date-range shell in every OTHER
     *  type. Daily<->Weekly also sync actual parameter values (matching vector structure);
     *  Monthly/Yearly/All-time only get an empty shell, since their scalar parameter model
     *  differs from Daily/Weekly's per-weekday vectors and is filled via Auto-calculate. */
    private suspend fun backfillFormShellsAndValues() {
        for (sourceType in ALL_PERIOD_TYPES) {
            if (sourceType == "ALLTIME") continue // singular, handled by ensureAllTimeFormExists
            val sourceForms = db.analysisFormDao().getFormsForOnce(generalActionId, sourceType)
            for (sourceForm in sourceForms) {
                for (targetType in ALL_PERIOD_TYPES) {
                    if (targetType == sourceType || targetType == "ALLTIME") continue
                    ensureShell(sourceForm, targetType, syncValues = sourceType in VECTOR_TYPES && targetType in VECTOR_TYPES)
                }
            }
        }
    }

    private suspend fun ensureShell(sourceForm: AnalysisForm, targetType: String, syncValues: Boolean) {
        val candidates = db.analysisFormDao().getFormsForOnce(sourceForm.generalActionId, targetType)
        var twin = candidates.firstOrNull { it.beginDate == sourceForm.beginDate && it.endDate == sourceForm.endDate }

        if (twin == null) {
            val overlaps = candidates.any { rangesOverlap(sourceForm.beginDate, sourceForm.endDate, it.beginDate, it.endDate) }
            if (overlaps) return
            val newId = db.analysisFormDao().insertForm(
                AnalysisForm(
                    dslaId = sourceForm.dslaId,
                    generalActionId = sourceForm.generalActionId,
                    periodType = targetType,
                    beginDate = sourceForm.beginDate,
                    endDate = sourceForm.endDate
                )
            )
            twin = db.analysisFormDao().getFormById(newId) ?: return
        }

        if (!syncValues) return

        val sourceParams = db.analysisFormDao().getParamsForFormOnce(sourceForm.id)
        val twinParams = db.analysisFormDao().getParamsForFormOnce(twin.id)
        for (sourceParam in sourceParams) {
            val existing = twinParams.firstOrNull { it.actionId == sourceParam.actionId && it.weekday == sourceParam.weekday }
            if (existing == null) {
                db.analysisFormDao().insertParam(
                    AnalysisFormActionParam(
                        formId = twin.id,
                        actionId = sourceParam.actionId,
                        weekday = sourceParam.weekday,
                        dimension = sourceParam.dimension,
                        timeVector = sourceParam.timeVector,
                        durationVector = sourceParam.durationVector,
                        quan1Vector = sourceParam.quan1Vector
                    )
                )
            } else if (existing.timeVector != sourceParam.timeVector ||
                existing.durationVector != sourceParam.durationVector ||
                existing.quan1Vector != sourceParam.quan1Vector ||
                existing.dimension != sourceParam.dimension
            ) {
                db.analysisFormDao().updateParam(
                    existing.copy(
                        dimension = sourceParam.dimension,
                        timeVector = sourceParam.timeVector,
                        durationVector = sourceParam.durationVector,
                        quan1Vector = sourceParam.quan1Vector
                    )
                )
            }
        }
    }

    private fun rangesOverlap(beginA: String, endA: String?, beginB: String, endB: String?): Boolean {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val startA = sdf.parse(beginA) ?: return true
        val finishA = endA?.let { sdf.parse(it) }
        val startB = sdf.parse(beginB) ?: return true
        val finishB = endB?.let { sdf.parse(it) }

        val aEndsAfterBStarts = finishA == null || !finishA.before(startB)
        val bEndsAfterAStarts = finishB == null || !finishB.before(startA)
        return aEndsAfterBStarts && bEndsAfterAStarts
    }

    private fun openEngineCheck() {
        val target = when (periodType) {
            "WEEKLY" -> WeeklyEngineCheckActivity::class.java
            "MONTHLY", "YEARLY", "ALLTIME" -> ScalarEngineCheckActivity::class.java
            else -> EngineCheckActivity::class.java
        }
        val intent = Intent(this, target).apply {
            putExtra("extra_dsla_id", dslaId)
            putExtra("extra_general_action_id", generalActionId)
            putExtra("extra_period_type", periodType)
        }
        startActivity(intent)
    }

    private fun observeForms() {
        lifecycleScope.launch {
            db.analysisFormDao().getFormsFor(generalActionId, periodType).collect { forms ->
                renderForms(sortFormsChronologically(forms))
            }
        }
    }

    private fun sortFormsChronologically(forms: List<AnalysisForm>): List<AnalysisForm> {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return forms.sortedBy { sdf.parse(it.beginDate)?.time ?: 0L }
    }

    private fun renderForms(forms: List<AnalysisForm>) {
        container.removeAllViews()

        if (forms.isEmpty()) {
            val empty = TextView(this)
            empty.text = getString(R.string.form_no_forms)
            empty.setTextColor(android.graphics.Color.DKGRAY)
            container.addView(empty)
            return
        }

        for ((index, form) in forms.withIndex()) {
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_form_entry, container, false)
            val title = itemView.findViewById<TextView>(R.id.form_entry_title)
            val range = itemView.findViewById<TextView>(R.id.form_entry_range)
            val btnDelete = itemView.findViewById<Button>(R.id.btn_form_delete)

            title.text = getString(R.string.form_number_format, index + 1)
            range.text = if (form.endDate != null) {
                getString(R.string.form_range_format, form.beginDate, form.endDate)
            } else {
                getString(R.string.form_range_ongoing, form.beginDate)
            }

            itemView.setOnClickListener {
                if (periodType in VECTOR_TYPES) {
                    val intent = Intent(this, WeekdaySelectActivity::class.java).apply {
                        putExtra(WeekdaySelectActivity.EXTRA_FORM_ID, form.id)
                        putExtra(WeekdaySelectActivity.EXTRA_GENERAL_ACTION_ID, generalActionId)
                    }
                    startActivity(intent)
                } else {
                    val intent = Intent(this, PeriodFormDetailActivity::class.java).apply {
                        putExtra(PeriodFormDetailActivity.EXTRA_FORM_ID, form.id)
                        putExtra(PeriodFormDetailActivity.EXTRA_GENERAL_ACTION_ID, generalActionId)
                    }
                    startActivity(intent)
                }
            }

            btnDelete.setOnClickListener {
                confirmDeleteForm(form)
            }

            container.addView(itemView)
        }
    }

    private fun confirmDeleteForm(form: AnalysisForm) {
        AlertDialog.Builder(this)
            .setMessage(R.string.form_delete_confirm)
            .setPositiveButton(R.string.form_delete_yes) { _, _ ->
                lifecycleScope.launch {
                    db.analysisFormDao().deleteParamsForForm(form.id)
                    db.analysisFormDao().deleteScalarParamsForForm(form.id)
                    db.analysisFormDao().deleteForm(form)
                }
            }
            .setNegativeButton(R.string.form_delete_no, null)
            .show()
    }

    private fun showCreateFormDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_form, null)
        val beginField = dialogView.findViewById<EditText>(R.id.edit_form_begin)
        val endField = dialogView.findViewById<EditText>(R.id.edit_form_end)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.form_save) { _, _ ->
                val begin = beginField.text.toString().trim()
                val end = endField.text.toString().trim().ifEmpty { null }

                if (begin.isEmpty()) {
                    Toast.makeText(this, R.string.form_begin_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val dsla = db.dslaDao().getById(dslaId) ?: return@launch

                    if (!isWithinDslaRange(begin, end, dsla.beginDate, dsla.endDate)) {
                        Toast.makeText(this@FormListActivity, R.string.form_outside_dsla_range, Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val existing = db.analysisFormDao().getFormsForOnce(generalActionId, periodType)
                    if (rangesOverlapAny(begin, end, existing)) {
                        Toast.makeText(this@FormListActivity, R.string.form_overlap_error, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val newId = db.analysisFormDao().insertForm(
                        AnalysisForm(
                            dslaId = dslaId,
                            generalActionId = generalActionId,
                            periodType = periodType,
                            beginDate = begin,
                            endDate = end
                        )
                    )
                    val newForm = db.analysisFormDao().getFormById(newId)
                    if (newForm != null) {
                        for (targetType in ALL_PERIOD_TYPES) {
                            if (targetType == periodType || targetType == "ALLTIME") continue
                            ensureShell(newForm, targetType, syncValues = periodType in VECTOR_TYPES && targetType in VECTOR_TYPES)
                        }
                        Toast.makeText(this@FormListActivity, R.string.form_mirrored_all, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.form_cancel, null)
            .show()
    }

    private fun isWithinDslaRange(formBegin: String, formEnd: String?, dslaBegin: String, dslaEnd: String?): Boolean {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val dslaBeginParsed = sdf.parse(dslaBegin) ?: return false
        val dslaEndParsed = dslaEnd?.let { sdf.parse(it) }
        val formBeginParsed = sdf.parse(formBegin) ?: return false
        val formEndParsed = formEnd?.let { sdf.parse(it) }

        if (formBeginParsed.before(dslaBeginParsed)) return false
        if (dslaEndParsed != null && formBeginParsed.after(dslaEndParsed)) return false

        if (formEndParsed != null) {
            if (formEndParsed.before(dslaBeginParsed)) return false
            if (dslaEndParsed != null && formEndParsed.after(dslaEndParsed)) return false
        } else {
            if (dslaEndParsed != null) return false
        }
        return true
    }

    private fun rangesOverlapAny(begin: String, end: String?, existing: List<AnalysisForm>): Boolean {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val newBegin = sdf.parse(begin) ?: return true
        val newEnd = end?.let { sdf.parse(it) }

        for (form in existing) {
            val existingBegin = sdf.parse(form.beginDate) ?: continue
            val existingEnd = form.endDate?.let { sdf.parse(it) }

            val newEndsAfterExistingBegins = newEnd == null || !newEnd.before(existingBegin)
            val existingEndsAfterNewBegins = existingEnd == null || !existingEnd.before(newBegin)

            if (newEndsAfterExistingBegins && existingEndsAfterNewBegins) {
                return true
            }
        }
        return false
    }
}