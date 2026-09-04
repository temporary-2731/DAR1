package com.dar.app

import android.content.Intent
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
import com.dar.app.data.AnalysisFormActionParam
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FormDetailActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var formId: Long = -1L
    private var generalActionId: Long = -1L
    private var weekday: Int = 1
    private var isEditable = true

    private lateinit var container: LinearLayout
    private lateinit var btnEditToggle: Button
    private lateinit var weekdayTitle: TextView

    private data class RowRefs(
        val action: ActionEntity,
        val dimensionField: EditText,
        val timeField: EditText,
        val durationField: EditText,
        val quan1Field: EditText
    )
    private val rowRefs = mutableListOf<RowRefs>()
    private lateinit var generalTimeText: TextView
    private lateinit var generalDurationText: TextView
    private lateinit var generalQuan1Text: TextView

    companion object {
        const val EXTRA_FORM_ID = "extra_form_id"
        const val EXTRA_GENERAL_ACTION_ID = "extra_general_action_id"
        const val EXTRA_WEEKDAY = "extra_weekday"
        private const val REQUEST_AUTO_FILL = 701

        private val weekdayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_detail)

        formId = intent.getLongExtra(EXTRA_FORM_ID, -1L)
        generalActionId = intent.getLongExtra(EXTRA_GENERAL_ACTION_ID, -1L)
        weekday = intent.getIntExtra(EXTRA_WEEKDAY, 1)
        db = AppDatabase.getInstance(applicationContext)

        container = findViewById(R.id.param_rows_container)
        btnEditToggle = findViewById(R.id.btn_param_edit_toggle)
        weekdayTitle = findViewById(R.id.param_weekday_title)
        weekdayTitle.text = weekdayNames[weekday - 1]

        findViewById<Button>(R.id.btn_param_save).setOnClickListener { attemptSave() }
        findViewById<Button>(R.id.btn_param_auto_fill).setOnClickListener { openAutoFillPicker() }
        btnEditToggle.setOnClickListener { toggleEdit() }

        generalTimeText = findViewById(R.id.general_time_value)
        generalDurationText = findViewById(R.id.general_duration_value)
        generalQuan1Text = findViewById(R.id.general_quan1_value)

        loadRows()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_AUTO_FILL && resultCode == RESULT_OK) {
            val count = data?.getIntExtra(AutoFillPickerActivity.RESULT_COPIED_COUNT, 0) ?: 0
            Toast.makeText(this, getString(R.string.auto_fill_result, count), Toast.LENGTH_SHORT).show()
            loadRows()
        }
    }

    private fun openAutoFillPicker() {
        val intent = Intent(this, AutoFillPickerActivity::class.java).apply {
            putExtra(AutoFillPickerActivity.EXTRA_TARGET_FORM_ID, formId)
            putExtra(AutoFillPickerActivity.EXTRA_TARGET_WEEKDAY, weekday)
            putExtra(AutoFillPickerActivity.EXTRA_TARGET_GENERAL_ACTION_ID, generalActionId)
        }
        startActivityForResult(intent, REQUEST_AUTO_FILL)
    }

    private fun toggleEdit() {
        isEditable = !isEditable
        applyEditableState()
    }

    private fun applyEditableState() {
        for (row in rowRefs) {
            row.dimensionField.isEnabled = isEditable
            row.timeField.isEnabled = isEditable
            row.durationField.isEnabled = isEditable
            row.quan1Field.isEnabled = isEditable
        }
        btnEditToggle.text = if (isEditable) getString(R.string.param_done_editing) else getString(R.string.param_edit)
    }

    private fun loadRows() {
        lifecycleScope.launch {
            val actions = db.generalActionDao().getActionsInGeneral(generalActionId).first()
            val existingParams = db.analysisFormDao().getParamsForForm(formId).first()
                .filter { it.weekday == weekday }

            container.removeAllViews()
            rowRefs.clear()

            for (action in actions) {
                val existing = existingParams.firstOrNull { it.actionId == action.id }
                addActionRow(action, existing)
            }

            applyEditableState()
            refreshGeneralRow()
        }
    }

    private fun addActionRow(action: ActionEntity, existing: AnalysisFormActionParam?) {
        val rowView = LayoutInflater.from(this).inflate(R.layout.item_param_row, container, false)

        val nameText = rowView.findViewById<TextView>(R.id.param_row_action_name)
        val dimensionField = rowView.findViewById<EditText>(R.id.edit_dimension)
        val timeField = rowView.findViewById<EditText>(R.id.edit_time_vector)
        val durationField = rowView.findViewById<EditText>(R.id.edit_duration_vector)
        val quan1Field = rowView.findViewById<EditText>(R.id.edit_quan1_vector)

        nameText.text = action.name
        dimensionField.setText((existing?.dimension ?: 1).toString())
        timeField.setText(existing?.timeVector ?: "")
        durationField.setText(existing?.durationVector ?: "")
        quan1Field.setText(existing?.quan1Vector ?: "")

        rowRefs.add(RowRefs(action, dimensionField, timeField, durationField, quan1Field))
        container.addView(rowView)
    }

    private fun refreshGeneralRow() {
        val allTime = mutableListOf<String>()
        val allDuration = mutableListOf<String>()
        val allQuan1 = mutableListOf<String>()

        for (row in rowRefs) {
            if (row.timeField.text.isNotBlank()) allTime.addAll(splitVector(row.timeField.text.toString()))
            if (row.durationField.text.isNotBlank()) allDuration.addAll(splitVector(row.durationField.text.toString()))
            if (row.quan1Field.text.isNotBlank()) allQuan1.addAll(splitVector(row.quan1Field.text.toString()))
        }

        generalTimeText.text = allTime.joinToString(", ")
        generalDurationText.text = allDuration.joinToString(", ")
        generalQuan1Text.text = allQuan1.joinToString(", ")
    }

    private fun splitVector(text: String): List<String> {
        return text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun validate(): Boolean {
        for ((index, row) in rowRefs.withIndex()) {
            val rowNumber = index + 1
            val dimension = row.dimensionField.text.toString().trim().toIntOrNull()
            if (dimension == null || dimension < 1 || dimension > 10) {
                Toast.makeText(this, getString(R.string.param_dimension_invalid, rowNumber), Toast.LENGTH_LONG).show()
                return false
            }

            val timeValues = splitVector(row.timeField.text.toString())
            val durationValues = splitVector(row.durationField.text.toString())
            val quan1Values = splitVector(row.quan1Field.text.toString())

            for ((label, values) in listOf(
                "Time" to timeValues,
                "Duration" to durationValues,
                "Quan1" to quan1Values
            )) {
                if (values.isEmpty()) continue
                if (values.size != dimension) {
                    Toast.makeText(
                        this,
                        getString(R.string.param_vector_length_mismatch, rowNumber, label, dimension, values.size),
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                }
                val numbers = values.map { it.toDoubleOrNull() }
                if (numbers.any { it == null }) {
                    Toast.makeText(this, getString(R.string.param_vector_invalid_number, rowNumber, label), Toast.LENGTH_LONG).show()
                    return false
                }
                if (numbers.any { it != null && it < 0.0 }) {
                    Toast.makeText(this, getString(R.string.param_vector_negative, rowNumber, label), Toast.LENGTH_LONG).show()
                    return false
                }
            }

            if (timeValues.size != timeValues.toSet().size) {
                Toast.makeText(this, getString(R.string.param_time_duplicate, rowNumber), Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun attemptSave() {
        if (!validate()) return

        lifecycleScope.launch {
            val existingParams = db.analysisFormDao().getParamsForForm(formId).first()
                .filter { it.weekday == weekday }

            for (row in rowRefs) {
                val dimension = row.dimensionField.text.toString().trim().toIntOrNull() ?: 1
                val timeVector = row.timeField.text.toString().trim()
                val durationVector = row.durationField.text.toString().trim()
                val quan1Vector = row.quan1Field.text.toString().trim()

                val existing = existingParams.firstOrNull { it.actionId == row.action.id }
                if (existing == null) {
                    db.analysisFormDao().insertParam(
                        AnalysisFormActionParam(
                            formId = formId,
                            actionId = row.action.id,
                            weekday = weekday,
                            dimension = dimension,
                            timeVector = timeVector,
                            durationVector = durationVector,
                            quan1Vector = quan1Vector
                        )
                    )
                } else {
                    db.analysisFormDao().updateParam(
                        existing.copy(
                            dimension = dimension,
                            timeVector = timeVector,
                            durationVector = durationVector,
                            quan1Vector = quan1Vector
                        )
                    )
                }
            }

            val mirrorSynced = ensureMirrorFormAndSyncWeekday()

            refreshGeneralRow()
            if (mirrorSynced) {
                Toast.makeText(this@FormDetailActivity, R.string.param_save_and_mirrored, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@FormDetailActivity, R.string.param_save, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Finds (or creates, if missing) the linked Daily/Weekly twin form covering the exact
     * same dates as this one, then copies this weekday's just-saved parameters into it —
     * keeping Daily and Weekly permanently in sync, in both directions, for both new and
     * previously-unlinked forms.
     */
    private suspend fun ensureMirrorFormAndSyncWeekday(): Boolean {
        val currentForm = db.analysisFormDao().getFormById(formId) ?: return false
        val otherType = when (currentForm.periodType) {
            "DAILY" -> "WEEKLY"
            "WEEKLY" -> "DAILY"
            else -> return false
        }

        val candidateMirrors = db.analysisFormDao().getFormsForOnce(currentForm.generalActionId, otherType)
        var mirrorForm: AnalysisForm? = candidateMirrors.firstOrNull {
            it.beginDate == currentForm.beginDate && it.endDate == currentForm.endDate
        }

        if (mirrorForm == null) {
            val overlaps = candidateMirrors.any { rangesOverlap(currentForm.beginDate, currentForm.endDate, it.beginDate, it.endDate) }
            if (overlaps) {
                return false // can't create a mirror that would overlap an unrelated existing form
            }
            val newId = db.analysisFormDao().insertForm(
                AnalysisForm(
                    dslaId = currentForm.dslaId,
                    generalActionId = currentForm.generalActionId,
                    periodType = otherType,
                    beginDate = currentForm.beginDate,
                    endDate = currentForm.endDate
                )
            )
            mirrorForm = db.analysisFormDao().getFormById(newId) ?: return false
        }

        val currentWeekdayParams = db.analysisFormDao().getParamsForFormWeekdayOnce(formId, weekday)
        val mirrorExistingParams = db.analysisFormDao().getParamsForFormWeekdayOnce(mirrorForm.id, weekday)

        for (sourceParam in currentWeekdayParams) {
            val existingMirrorParam = mirrorExistingParams.firstOrNull { it.actionId == sourceParam.actionId }
            if (existingMirrorParam == null) {
                db.analysisFormDao().insertParam(
                    AnalysisFormActionParam(
                        formId = mirrorForm.id,
                        actionId = sourceParam.actionId,
                        weekday = weekday,
                        dimension = sourceParam.dimension,
                        timeVector = sourceParam.timeVector,
                        durationVector = sourceParam.durationVector,
                        quan1Vector = sourceParam.quan1Vector
                    )
                )
            } else {
                db.analysisFormDao().updateParam(
                    existingMirrorParam.copy(
                        dimension = sourceParam.dimension,
                        timeVector = sourceParam.timeVector,
                        durationVector = sourceParam.durationVector,
                        quan1Vector = sourceParam.quan1Vector
                    )
                )
            }
        }
        return true
    }

    private fun rangesOverlap(beginA: String, endA: String?, beginB: String, endB: String?): Boolean {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        val startA = sdf.parse(beginA) ?: return true
        val finishA = endA?.let { sdf.parse(it) }
        val startB = sdf.parse(beginB) ?: return true
        val finishB = endB?.let { sdf.parse(it) }

        val aEndsAfterBStarts = finishA == null || !finishA.before(startB)
        val bEndsAfterAStarts = finishB == null || !finishB.before(startA)
        return aEndsAfterBStarts && bEndsAfterAStarts
    }
}