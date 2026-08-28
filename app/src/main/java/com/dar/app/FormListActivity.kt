package com.dar.app

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

        observeForms()
    }

    private fun observeForms() {
        lifecycleScope.launch {
            db.analysisFormDao().getFormsFor(generalActionId, periodType).collect { forms ->
                renderForms(sortFormsChronologically(forms))
            }
        }
    }

    /** Sorts by actual parsed date, since the stored beginDate is plain DD/MM/YYYY text
     *  and would otherwise sort alphabetically (e.g. "25/01" before "03/02"). */
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
                Toast.makeText(this, R.string.analysis_coming_soon, Toast.LENGTH_SHORT).show()
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
                    val dsla = db.dslaDao().getById(dslaId)
                    if (dsla == null) {
                        return@launch
                    }

                    if (!isWithinDslaRange(begin, end, dsla.beginDate, dsla.endDate)) {
                        Toast.makeText(this@FormListActivity, R.string.form_outside_dsla_range, Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val existing = db.analysisFormDao().getFormsForOnce(generalActionId, periodType)
                    if (rangesOverlapAny(begin, end, existing)) {
                        Toast.makeText(this@FormListActivity, R.string.form_overlap_error, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    db.analysisFormDao().insertForm(
                        AnalysisForm(
                            dslaId = dslaId,
                            generalActionId = generalActionId,
                            periodType = periodType,
                            beginDate = begin,
                            endDate = end
                        )
                    )
                }
            }
            .setNegativeButton(R.string.form_cancel, null)
            .show()
    }

    /** Both the form's begin and end (if set) must fall within [dslaBegin, dslaEnd or unbounded]. */
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
            // An ongoing form is only valid if the DSLA itself is ongoing.
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
