package com.dar.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.ActionEntity
import com.dar.app.data.AppDatabase
import com.dar.app.data.RecordingRow
import com.dar.app.databinding.ActivityRecordingBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L
    private lateinit var todayDate: String
    private var timeEnabled: Boolean = true

    private var libraryActions: List<ActionEntity> = emptyList()
    private lateinit var actionNameAdapter: ArrayAdapter<String>

    private data class RowBinding(
        var row: RecordingRow,
        val actionField: AutoCompleteTextView,
        val timeField: EditText?,
        val durationView: TextView?,
        val quanFields: List<EditText>,
        val commentField: EditText
    )

    private val rowBindings = mutableListOf<RowBinding>()
    private val fieldMatrix = mutableListOf<MutableList<EditText>>()
    private var currentRow = 0
    private var currentCol = 0

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        db = AppDatabase.getInstance(applicationContext)

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        todayDate = sdf.format(Date())
        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
        binding.dateHeader.text = "$todayDate  $dayName"

        binding.btnAddRow.setOnClickListener { addNewRow() }
        binding.btnSave.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { confirmCancel() }

        binding.btnArrowLeft.setOnClickListener { moveLeft() }
        binding.btnArrowRight.setOnClickListener { moveRight() }
        binding.btnArrowUp.setOnClickListener { moveUp() }
        binding.btnArrowDown.setOnClickListener { moveDown() }

        actionNameAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())

        loadAndRenderRows()
    }

    private fun loadAndRenderRows() {
        lifecycleScope.launch {
            val dsla = db.dslaDao().getById(dslaId)
            timeEnabled = dsla?.timeEnabled ?: true

            libraryActions = db.actionDao().getActiveSortedByFrequency(dslaId).first()
            actionNameAdapter.clear()
            actionNameAdapter.addAll(libraryActions.map { it.name })
            actionNameAdapter.notifyDataSetChanged()

            var rows = db.recordingDao().getRowsForDate(dslaId, todayDate)
            if (rows.isEmpty()) {
                db.recordingDao().insert(
                    RecordingRow(dslaId = dslaId, date = todayDate, rowNumber = 1)
                )
                rows = db.recordingDao().getRowsForDate(dslaId, todayDate)
            }

            renderRows(rows)
        }
    }

    private fun renderRows(rows: List<RecordingRow>) {
        binding.rowContainer.removeAllViews()
        rowBindings.clear()
        for (row in rows) {
            addRowView(row)
        }
        recomputeAllDurations()
        buildFieldMatrix()
    }

    private fun addRowView(row: RecordingRow) {
        val layoutRes = if (timeEnabled) {
            R.layout.item_recording_row_time
        } else {
            R.layout.item_recording_row_notime
        }
        val rowView = LayoutInflater.from(this).inflate(layoutRes, binding.rowContainer, false)

        val rowLabel = rowView.findViewById<TextView>(R.id.row_number_label)
        val actionField = rowView.findViewById<AutoCompleteTextView>(R.id.edit_action_name)
        val commentField = rowView.findViewById<EditText>(R.id.edit_comment)

        rowLabel.text = row.rowNumber.toString()
        actionField.setText(row.actionName)
        commentField.setText(row.comment)

        actionField.setAdapter(actionNameAdapter)
        actionField.threshold = 1
        actionField.setOnItemClickListener { _, _, position, _ ->
            val selectedName = actionNameAdapter.getItem(position)
            val matched = libraryActions.firstOrNull { it.name == selectedName }
            if (matched != null) {
                lifecycleScope.launch {
                    db.actionDao().incrementUsage(matched.id)
                }
            }
        }

        var timeField: EditText? = null
        var durationView: TextView? = null
        val quanFields = mutableListOf<EditText>()

        if (timeEnabled) {
            timeField = rowView.findViewById(R.id.edit_time)
            durationView = rowView.findViewById(R.id.view_duration)
            val quan1 = rowView.findViewById<EditText>(R.id.edit_quan1)

            timeField.setText(row.timeValue)
            quan1.setText(row.quan1)
            quanFields.add(quan1)
        } else {
            val quan1 = rowView.findViewById<EditText>(R.id.edit_quan1)
            val quan2 = rowView.findViewById<EditText>(R.id.edit_quan2)
            val quan3 = rowView.findViewById<EditText>(R.id.edit_quan3)

            quan1.setText(row.quan1)
            quan2.setText(row.quan2)
            quan3.setText(row.quan3)
            quanFields.add(quan1)
            quanFields.add(quan2)
            quanFields.add(quan3)
        }

        val binder = RowBinding(
            row = row,
            actionField = actionField,
            timeField = timeField,
            durationView = durationView,
            quanFields = quanFields,
            commentField = commentField
        )
        rowBindings.add(binder)

        actionField.addTextChangedListener(simpleWatcher { text ->
            binder.row = binder.row.copy(actionName = text)
            persistRow(binder.row)
        })

        commentField.addTextChangedListener(simpleWatcher { text ->
            binder.row = binder.row.copy(comment = text)
            persistRow(binder.row)
        })

        if (timeEnabled) {
            timeField?.addTextChangedListener(simpleWatcher { text ->
                binder.row = binder.row.copy(timeValue = text)
                persistRow(binder.row)
                recomputeAllDurations()
            })
            quanFields.getOrNull(0)?.addTextChangedListener(simpleWatcher { text ->
                binder.row = binder.row.copy(quan1 = text)
                persistRow(binder.row)
            })
        } else {
            quanFields.getOrNull(0)?.addTextChangedListener(simpleWatcher { text ->
                binder.row = binder.row.copy(quan1 = text)
                persistRow(binder.row)
            })
            quanFields.getOrNull(1)?.addTextChangedListener(simpleWatcher { text ->
                binder.row = binder.row.copy(quan2 = text)
                persistRow(binder.row)
            })
            quanFields.getOrNull(2)?.addTextChangedListener(simpleWatcher { text ->
                binder.row = binder.row.copy(quan3 = text)
                persistRow(binder.row)
            })
        }

        binding.rowContainer.addView(rowView)
    }

    private fun buildFieldMatrix() {
        fieldMatrix.clear()
        for ((rowIndex, binder) in rowBindings.withIndex()) {
            val fields = mutableListOf<EditText>()
            fields.add(binder.actionField)
            if (timeEnabled) {
                binder.timeField?.let { fields.add(it) }
                binder.quanFields.getOrNull(0)?.let { fields.add(it) }
            } else {
                fields.addAll(binder.quanFields)
            }
            fields.add(binder.commentField)
            fieldMatrix.add(fields)

            for ((colIndex, field) in fields.withIndex()) {
                field.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        currentRow = rowIndex
                        currentCol = colIndex
                    }
                }
            }
        }
    }

    private fun focusCell(row: Int, col: Int) {
        val rowFields = fieldMatrix.getOrNull(row) ?: return
        val clampedCol = col.coerceIn(0, rowFields.size - 1)
        rowFields[clampedCol].requestFocus()
    }

    private fun moveUp() {
        if (currentRow > 0) focusCell(currentRow - 1, currentCol)
    }

    private fun moveDown() {
        if (currentRow < fieldMatrix.size - 1) {
            focusCell(currentRow + 1, currentCol)
        } else {
            addNewRow(focusCol = currentCol)
        }
    }

    private fun moveLeft() {
        if (currentCol > 0) focusCell(currentRow, currentCol - 1)
    }

    private fun moveRight() {
        val row = fieldMatrix.getOrNull(currentRow) ?: return
        if (currentCol < row.size - 1) focusCell(currentRow, currentCol + 1)
    }

    private fun recomputeAllDurations() {
        if (!timeEnabled) return

        for (i in rowBindings.indices) {
            val current = rowBindings[i]
            val durationText: String = if (i < rowBindings.size - 1) {
                val currentMinutes = parseTimeToMinutes(current.row.timeValue)
                val nextMinutes = parseTimeToMinutes(rowBindings[i + 1].row.timeValue)
                if (currentMinutes != null && nextMinutes != null) {
                    (nextMinutes - currentMinutes).toString()
                } else {
                    ""
                }
            } else {
                ""
            }

            current.row = current.row.copy(durationValue = durationText)
            current.durationView?.text = durationText.ifEmpty { getString(R.string.recording_duration_pending) }
            persistRow(current.row)
        }
    }

    private fun parseTimeToMinutes(value: String): Int? {
        if (value.isBlank()) return null
        val floatValue = value.toFloatOrNull() ?: return null
        if (floatValue < 0) return null
        val hours = floor(floatValue).toInt()
        val fractional = floatValue - hours
        val minutes = Math.round(fractional * 100f)
        if (minutes >= 60) return null
        return hours * 60 + minutes
    }

    private fun persistRow(row: RecordingRow) {
        lifecycleScope.launch {
            db.recordingDao().update(row)
        }
    }

    private fun simpleWatcher(onChanged: (String) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onChanged(s?.toString() ?: "")
            }
        }
    }

    private fun addNewRow(focusCol: Int? = null) {
        lifecycleScope.launch {
            val currentCount = db.recordingDao().countForDate(dslaId, todayDate)
            db.recordingDao().insert(
                RecordingRow(dslaId = dslaId, date = todayDate, rowNumber = currentCount + 1)
            )
            val rows = db.recordingDao().getRowsForDate(dslaId, todayDate)
            renderRows(rows)
            if (focusCol != null) {
                focusCell(fieldMatrix.size - 1, focusCol)
            }
        }
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setMessage(R.string.recording_cancel_confirm)
            .setPositiveButton(R.string.recording_cancel_yes) { _, _ ->
                lifecycleScope.launch {
                    db.recordingDao().deleteAllForDate(dslaId, todayDate)
                    finish()
                }
            }
            .setNegativeButton(R.string.recording_cancel_no, null)
            .show()
    }
}
