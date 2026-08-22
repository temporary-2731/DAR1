package com.dar.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AppDatabase
import com.dar.app.data.RecordingRow
import com.dar.app.databinding.ActivityRecordingBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L
    private lateinit var todayDate: String

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

        loadExistingRows()
    }

    private fun loadExistingRows() {
        lifecycleScope.launch {
            val existingCount = db.recordingDao().countForDate(dslaId, todayDate)
            if (existingCount == 0) {
                addNewRow()
            }
            db.recordingDao().getRowsForDate(dslaId, todayDate).collect { rows ->
                binding.rowContainer.removeAllViews()
                for (row in rows) {
                    addRowView(row)
                }
            }
        }
    }

    private fun addNewRow() {
        lifecycleScope.launch {
            val currentCount = db.recordingDao().countForDate(dslaId, todayDate)
            val newRow = RecordingRow(
                dslaId = dslaId,
                date = todayDate,
                rowNumber = currentCount + 1
            )
            db.recordingDao().insert(newRow)
        }
    }

    private fun addRowView(row: RecordingRow) {
        val rowView = LayoutInflater.from(this)
            .inflate(R.layout.item_recording_row, binding.rowContainer, false)

        val rowLabel = rowView.findViewById<android.widget.TextView>(R.id.row_number_label)
        val actionField = rowView.findViewById<EditText>(R.id.edit_action_name)
        val quanField = rowView.findViewById<EditText>(R.id.edit_quan1)
        val commentField = rowView.findViewById<EditText>(R.id.edit_comment)

        rowLabel.text = row.rowNumber.toString()
        actionField.setText(row.actionName)
        quanField.setText(row.quan1)
        commentField.setText(row.comment)

        actionField.addTextChangedListener(simpleWatcher { text ->
            saveRowField(row.copy(actionName = text))
        })
        quanField.addTextChangedListener(simpleWatcher { text ->
            saveRowField(row.copy(quan1 = text))
        })
        commentField.addTextChangedListener(simpleWatcher { text ->
            saveRowField(row.copy(comment = text))
        })

        binding.rowContainer.addView(rowView)
    }

    private fun saveRowField(updatedRow: RecordingRow) {
        lifecycleScope.launch {
            db.recordingDao().update(updatedRow)
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
