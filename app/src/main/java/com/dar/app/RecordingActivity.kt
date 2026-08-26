package com.dar.app

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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

class RecordingActivity : AppCompatActivity() {

    lateinit var binding: ActivityRecordingBinding
    lateinit var db: AppDatabase
    var dslaId: Long = -1L
    lateinit var todayDate: String
    var timeEnabled: Boolean = true

    var libraryActions: List<ActionEntity> = emptyList()
    lateinit var actionNameAdapter: ArrayAdapter<String>
    var clipboard: ClipboardContent? = null

    val rowBindings = mutableListOf<RowBinding>()
    val fieldMatrix = mutableListOf<MutableList<EditText>>()
    var currentRow = 0
    var currentCol = 0

    // Multi-cell selection state
    var selectionActive = false
    var anchorRow = -1
    var anchorCol = -1
    var extentRow = -1
    var extentCol = -1
    val highlightedFields = mutableListOf<EditText>()
    var needsNewAnchor = false

    // Whole-row selection state
    var rowSelectionActive = false
    val selectedRowIndices = mutableSetOf<Int>()
    var rowSelectionNeedsNewAnchor = false

    // Undo/Redo stacks
    val undoStack = ArrayDeque<RecordingSnapshot>()
    val redoStack = ArrayDeque<RecordingSnapshot>()
    var suppressSnapshotCapture = false

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

        binding.btnAddRow.setOnClickListener {
            captureUndoSnapshot()
            addNewRow()
        }
        binding.btnSave.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { confirmCancel() }

        binding.btnArrowLeft.setOnClickListener { moveLeft() }
        binding.btnArrowRight.setOnClickListener { moveRight() }
        binding.btnArrowUp.setOnClickListener { moveUp() }
        binding.btnArrowDown.setOnClickListener { moveDown() }

        binding.btnSelCopy.setOnClickListener { copySelection() }
        binding.btnSelCut.setOnClickListener { captureUndoSnapshot(); cutSelection() }
        binding.btnSelDelete.setOnClickListener { captureUndoSnapshot(); deleteSelection() }
        binding.btnSelPaste.setOnClickListener { captureUndoSnapshot(); pasteSelection() }
        binding.btnSelDone.setOnClickListener { endSelection() }

        binding.btnRowSelCopy.setOnClickListener { copyRowSelection() }
        binding.btnRowSelCut.setOnClickListener { captureUndoSnapshot(); cutRowSelection() }
        binding.btnRowSelDelete.setOnClickListener { captureUndoSnapshot(); deleteRowSelection() }
        binding.btnRowSelPaste.setOnClickListener { captureUndoSnapshot(); pasteRowSelection() }
        binding.btnRowSelDone.setOnClickListener { endRowSelection() }

        binding.btnUndo.setOnClickListener { performUndo() }
        binding.btnRedo.setOnClickListener { performRedo() }

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

    fun columnTypesForMode(): List<FieldType> {
        return if (timeEnabled) {
            listOf(FieldType.ACTION, FieldType.TIME, FieldType.QUAN1, FieldType.COMMENT)
        } else {
            listOf(FieldType.ACTION, FieldType.QUAN1, FieldType.QUAN2, FieldType.QUAN3, FieldType.COMMENT)
        }
    }

    fun renderRows(rows: List<RecordingRow>) {
        endSelection()
        binding.rowContainer.removeAllViews()
        rowBindings.clear()

        addHeaderView()
        for (row in rows) {
            addRowView(row)
        }
        recomputeAllDurations()
        buildFieldMatrix()
        updateUndoRedoButtons()
    }

    private fun addHeaderView() {
        val layoutRes = if (timeEnabled) {
            R.layout.item_recording_header_time
        } else {
            R.layout.item_recording_header_notime
        }
        val headerView = LayoutInflater.from(this).inflate(layoutRes, binding.rowContainer, false)
        binding.rowContainer.addView(headerView)
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
            rowLabel = rowLabel,
            actionField = actionField,
            timeField = timeField,
            durationView = durationView,
            quanFields = quanFields,
            commentField = commentField,
            committedActionName = row.actionName
        )
        rowBindings.add(binder)
        val thisRowIndex = rowBindings.size - 1

        actionField.setOnItemClickListener { _, _, position, _ ->
            val selectedName = actionNameAdapter.getItem(position)
            val matched = libraryActions.firstOrNull { it.name == selectedName }
            if (matched != null) {
                binder.committedActionName = matched.name
                binder.row = binder.row.copy(actionName = matched.name)
                persistRow(binder.row)
                lifecycleScope.launch {
                    db.actionDao().incrementUsage(matched.id)
                }
            }
        }

        actionField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!suppressSnapshotCapture) captureUndoSnapshot()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (binder.isRevertingActionText) return
                val newText = s?.toString() ?: ""

                if (newText.isEmpty()) {
                    binder.row = binder.row.copy(actionName = "")
                    persistRow(binder.row)
                    return
                }

                val hasPrefixMatch = libraryActions.any { it.name.startsWith(newText, ignoreCase = true) }
                if (hasPrefixMatch) {
                    binder.row = binder.row.copy(actionName = newText)
                    persistRow(binder.row)
                } else {
                    binder.isRevertingActionText = true
                    val revertTo = binder.row.actionName
                    actionField.setText(revertTo)
                    actionField.setSelection(revertTo.length)
                    binder.isRevertingActionText = false
                    Toast.makeText(this@RecordingActivity, R.string.recording_invalid_action, Toast.LENGTH_SHORT).show()
                }
            }
        })

        actionField.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                currentRow = thisRowIndex
                currentCol = fieldMatrix.getOrNull(thisRowIndex)?.indexOf(actionField) ?: 0
            } else {
                val typed = actionField.text.toString().trim()
                if (typed.isEmpty()) {
                    binder.committedActionName = ""
                    return@setOnFocusChangeListener
                }
                val exactMatch = libraryActions.firstOrNull { it.name.equals(typed, ignoreCase = true) }
                if (exactMatch != null) {
                    actionField.setText(exactMatch.name)
                    binder.committedActionName = exactMatch.name
                    binder.row = binder.row.copy(actionName = exactMatch.name)
                    persistRow(binder.row)
                    return@setOnFocusChangeListener
                }
                val prefixMatches = libraryActions.filter { it.name.startsWith(typed, ignoreCase = true) }
                if (prefixMatches.size == 1) {
                    val onlyMatch = prefixMatches[0]
                    actionField.setText(onlyMatch.name)
                    binder.committedActionName = onlyMatch.name
                    binder.row = binder.row.copy(actionName = onlyMatch.name)
                    persistRow(binder.row)
                } else {
                    actionField.setText(binder.committedActionName)
                    binder.row = binder.row.copy(actionName = binder.committedActionName)
                    persistRow(binder.row)
                }
            }
        }

        commentField.addTextChangedListener(snapshotWatcher(binder) { text ->
            binder.row = binder.row.copy(comment = text)
            persistRow(binder.row)
        })

        if (timeEnabled) {
            timeField?.addTextChangedListener(snapshotWatcher(binder) { text ->
                binder.row = binder.row.copy(timeValue = text)
                persistRow(binder.row)
                recomputeAllDurations()
            })
            quanFields.getOrNull(0)?.addTextChangedListener(snapshotWatcher(binder) { text ->
                binder.row = binder.row.copy(quan1 = text)
                persistRow(binder.row)
            })
        } else {
            quanFields.getOrNull(0)?.addTextChangedListener(snapshotWatcher(binder) { text ->
                binder.row = binder.row.copy(quan1 = text)
                persistRow(binder.row)
            })
            quanFields.getOrNull(1)?.addTextChangedListener(snapshotWatcher(binder) { text ->
                binder.row = binder.row.copy(quan2 = text)
                persistRow(binder.row)
            })
            quanFields.getOrNull(2)?.addTextChangedListener(snapshotWatcher(binder) { text ->
                binder.row = binder.row.copy(quan3 = text)
                persistRow(binder.row)
            })
        }

        rowLabel.isLongClickable = true
        rowLabel.setOnLongClickListener {
            showRowMenu(binder, thisRowIndex)
            true
        }
        rowLabel.setOnClickListener {
            if (rowSelectionActive) {
                if (rowSelectionNeedsNewAnchor) {
                    setRowPasteAnchor(thisRowIndex)
                } else {
                    toggleRowSelection(thisRowIndex)
                }
            }
        }

        actionField.setOnLongClickListener {
            showCellMenu(binder, FieldType.ACTION, thisRowIndex)
            true
        }
        commentField.setOnLongClickListener {
            showCellMenu(binder, FieldType.COMMENT, thisRowIndex)
            true
        }
        if (timeEnabled) {
            timeField?.setOnLongClickListener {
                showCellMenu(binder, FieldType.TIME, thisRowIndex)
                true
            }
            quanFields.getOrNull(0)?.setOnLongClickListener {
                showCellMenu(binder, FieldType.QUAN1, thisRowIndex)
                true
            }
        } else {
            quanFields.getOrNull(0)?.setOnLongClickListener {
                showCellMenu(binder, FieldType.QUAN1, thisRowIndex)
                true
            }
            quanFields.getOrNull(1)?.setOnLongClickListener {
                showCellMenu(binder, FieldType.QUAN2, thisRowIndex)
                true
            }
            quanFields.getOrNull(2)?.setOnLongClickListener {
                showCellMenu(binder, FieldType.QUAN3, thisRowIndex)
                true
            }
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
                if (field != binder.actionField) {
                    field.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) {
                            currentRow = rowIndex
                            currentCol = colIndex
                        }
                    }
                }
                field.setOnClickListener {
                    if (selectionActive) {
                        if (needsNewAnchor) {
                            beginNewAnchor(rowIndex, colIndex)
                        } else {
                            extendSelection(rowIndex, colIndex)
                        }
                    }
                }
            }
        }
    }

    fun focusCell(row: Int, col: Int) {
        val rowFields = fieldMatrix.getOrNull(row) ?: return
        val clampedCol = col.coerceIn(0, rowFields.size - 1)
        val field = rowFields[clampedCol]
        field.requestFocus()
        field.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun moveUp() {
        if (currentRow > 0) focusCell(currentRow - 1, currentCol)
    }

    private fun moveDown() {
        if (currentRow < fieldMatrix.size - 1) {
            focusCell(currentRow + 1, currentCol)
        } else {
            captureUndoSnapshot()
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

    fun persistRow(row: RecordingRow) {
        lifecycleScope.launch {
            db.recordingDao().update(row)
        }
    }

    private fun snapshotWatcher(binder: RowBinding, onChanged: (String) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!suppressSnapshotCapture) captureUndoSnapshot()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onChanged(s?.toString() ?: "")
            }
        }
    }

    fun addNewRow(focusCol: Int? = null) {
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
