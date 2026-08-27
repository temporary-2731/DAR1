package com.dar.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RecordingActivity : AppCompatActivity() {

    lateinit var binding: ActivityRecordingBinding
    lateinit var db: AppDatabase
    var dslaId: Long = -1L
    lateinit var todayDate: String
    var timeEnabled: Boolean = true

    var mode: RecordingMode = RecordingMode.RECORDING
    var isEditable: Boolean = true

    var dslaBeginDate: String = ""
    var dslaEndDate: String? = null

    var libraryActions: List<ActionEntity> = emptyList()
    lateinit var actionNameAdapter: ArrayAdapter<String>
    var clipboard: ClipboardContent? = null

    val rowBindings = mutableListOf<RowBinding>()
    val fieldMatrix = mutableListOf<MutableList<EditText>>()
    var currentRow = 0
    var currentCol = 0

    var selectionActive = false
    var anchorRow = -1
    var anchorCol = -1
    var extentRow = -1
    var extentCol = -1
    val highlightedFields = mutableListOf<EditText>()
    var needsNewAnchor = false

    var rowSelectionActive = false
    val selectedRowIndices = mutableSetOf<Int>()
    var rowSelectionNeedsNewAnchor = false

    val undoStack = ArrayDeque<RecordingSnapshot>()
    val redoStack = ArrayDeque<RecordingSnapshot>()
    var suppressSnapshotCapture = false

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_TARGET_DATE = "extra_target_date"
        private const val DATE_FORMAT = "dd/MM/yyyy"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        db = AppDatabase.getInstance(applicationContext)

        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val passedDate = intent.getStringExtra(EXTRA_TARGET_DATE)
        mode = if (intent.getStringExtra(EXTRA_MODE) == "HISTORY") {
            RecordingMode.HISTORY
        } else {
            RecordingMode.RECORDING
        }

        todayDate = if (mode == RecordingMode.HISTORY) {
            passedDate ?: sdf.format(Date())
        } else {
            sdf.format(Date())
        }

        isEditable = (mode == RecordingMode.RECORDING)

        updateDateHeaderText()

        binding.btnAddRow.setOnClickListener {
            captureUndoSnapshot()
            addNewRow()
        }
        binding.btnSave.setOnClickListener { attemptSave() }
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

        binding.btnEditToggle.setOnClickListener { toggleEdit() }
        binding.btnHighlightMode.setOnClickListener { openHighlightMode() }
        binding.btnDatePrev.setOnClickListener { navigateDate(-1) }
        binding.btnDateNext.setOnClickListener { navigateDate(1) }

        actionNameAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())

        applyModeUi()
        loadInitialConfig()
    }

    private fun applyModeUi() {
        if (mode == RecordingMode.HISTORY) {
            binding.historyTopBar.visibility = View.VISIBLE
            binding.btnDatePrev.visibility = View.VISIBLE
            binding.btnDateNext.visibility = View.VISIBLE
            binding.btnCancel.visibility = View.GONE
            binding.btnSave.text = getString(R.string.history_close)
        } else {
            binding.historyTopBar.visibility = View.GONE
            binding.btnDatePrev.visibility = View.GONE
            binding.btnDateNext.visibility = View.GONE
            binding.btnCancel.visibility = View.VISIBLE
            binding.btnSave.text = getString(R.string.recording_save)
        }
    }

    private fun updateEditToggleButtonText() {
        binding.btnEditToggle.text = if (isEditable) {
            getString(R.string.history_done_editing)
        } else {
            getString(R.string.history_edit)
        }
    }

    private fun toggleEdit() {
        isEditable = !isEditable
        if (isEditable && rowBindings.isEmpty()) {
            loadRowsForCurrentDate()
        } else {
            applyEditableStateToFields()
        }
    }

    private fun applyEditableStateToFields() {
        for (binder in rowBindings) {
            binder.rowLabel.isEnabled = isEditable
            binder.actionField.isEnabled = isEditable
            binder.timeField?.isEnabled = isEditable
            for (q in binder.quanFields) q.isEnabled = isEditable
            binder.commentField.isEnabled = isEditable
        }
        if (mode == RecordingMode.HISTORY) {
            val toolbarVisibility = if (isEditable) View.VISIBLE else View.GONE
            binding.arrowBar.visibility = toolbarVisibility
            binding.undoRedoBar.visibility = toolbarVisibility
            binding.btnAddRow.visibility = toolbarVisibility
        }
        updateEditToggleButtonText()
    }

    private fun updateDateHeaderText() {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val parsed = sdf.parse(todayDate) ?: Date()
        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(parsed)
        binding.dateHeader.text = "$todayDate  $dayName"
    }

    private fun upperBoundDate(): Date {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val today = sdf.parse(sdf.format(Date())) ?: Date()
        val end = dslaEndDate?.let { sdf.parse(it) }
        return if (end != null && end.before(today)) end else today
    }

    private fun lowerBoundDate(): Date {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return sdf.parse(dslaBeginDate) ?: sdf.parse(sdf.format(Date())) ?: Date()
    }

    private fun updateDateNavButtons() {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val current = sdf.parse(todayDate) ?: Date()
        val upper = upperBoundDate()
        val lower = lowerBoundDate()

        val atUpper = !current.before(upper)
        val atLower = !current.after(lower)

        binding.btnDateNext.isEnabled = !atUpper
        binding.btnDateNext.alpha = if (atUpper) 0.4f else 1f
        binding.btnDatePrev.isEnabled = !atLower
        binding.btnDatePrev.alpha = if (atLower) 0.4f else 1f
    }

    private fun navigateDate(deltaDays: Int) {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(todayDate) ?: Date()
        cal.add(Calendar.DAY_OF_MONTH, deltaDays)
        val newDate = cal.time

        val upper = upperBoundDate()
        val lower = lowerBoundDate()
        if (newDate.after(upper) || newDate.before(lower)) return

        todayDate = sdf.format(newDate)
        undoStack.clear()
        redoStack.clear()
        updateDateHeaderText()
        updateDateNavButtons()
        loadRowsForCurrentDate()
    }

    private fun openHighlightMode() {
        val intent = Intent(this, HighlightModeActivity::class.java).apply {
            putExtra(HighlightModeActivity.EXTRA_DSLA_ID, dslaId)
        }
        startActivity(intent)
    }

    private fun loadInitialConfig() {
        lifecycleScope.launch {
            val dsla = db.dslaDao().getById(dslaId)
            timeEnabled = dsla?.timeEnabled ?: true

            val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            dslaBeginDate = dsla?.beginDate?.ifEmpty { sdf.format(Date()) } ?: sdf.format(Date())
            dslaEndDate = dsla?.endDate

            if (mode == RecordingMode.RECORDING) {
                val end = dslaEndDate
                if (end != null) {
                    val endParsed = sdf.parse(end)
                    val today = sdf.parse(sdf.format(Date()))
                    if (endParsed != null && today != null && endParsed.before(today)) {
                        Toast.makeText(
                            this@RecordingActivity,
                            getString(R.string.recording_dsla_ended_message, end),
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                        return@launch
                    }
                }
            }

            libraryActions = db.actionDao().getActiveSortedByFrequency(dslaId).first()
            actionNameAdapter.clear()
            actionNameAdapter.addAll(libraryActions.map { it.name })
            actionNameAdapter.notifyDataSetChanged()

            if (mode == RecordingMode.HISTORY) {
                updateDateNavButtons()
            }

            loadRowsForCurrentDate()
        }
    }

    private fun loadRowsForCurrentDate() {
        lifecycleScope.launch {
            var rows = db.recordingDao().getRowsForDate(dslaId, todayDate)
            val shouldAutoCreate = rows.isEmpty() &&
                (mode == RecordingMode.RECORDING || (mode == RecordingMode.HISTORY && isEditable))

            if (shouldAutoCreate) {
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
        applyEditableStateToFields()
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
            committedActionName = row.actionName,
            committedTimeValue = row.timeValue
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
            timeField?.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    if (!suppressSnapshotCapture) captureUndoSnapshot()
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (binder.isRevertingTimeText) return
                    handleTimeInput(binder, thisRowIndex, timeField, s?.toString() ?: "")
                }
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

    /**
     * Validates a Time cell in real time, mirroring the Action field's strict-block pattern:
     * - Blank is always allowed (clears the cell).
     * - A value whose minute component is outside [00,59) is rejected immediately.
     * - (24-hour mode) A value not strictly later than the previous row's committed time
     *   is rejected immediately. 12-hour wraparound is deferred until the format toggle
     *   exists in Tools.
     */
    private fun handleTimeInput(binder: RowBinding, rowIndex: Int, timeField: EditText, newText: String) {
        if (newText.isBlank()) {
            binder.committedTimeValue = ""
            binder.row = binder.row.copy(timeValue = "")
            persistRow(binder.row)
            recomputeAllDurations()
            return
        }

        val newMinutes = parseTimeToMinutes(newText)
        if (newMinutes == null) {
            revertTimeField(binder, timeField)
            Toast.makeText(this, getString(R.string.recording_time_invalid_minutes, rowIndex + 1), Toast.LENGTH_SHORT).show()
            return
        }

        if (rowIndex > 0) {
            val prevCommitted = rowBindings[rowIndex - 1].committedTimeValue
            if (prevCommitted.isNotBlank()) {
                val prevMinutes = parseTimeToMinutes(prevCommitted)
                if (prevMinutes != null && newMinutes <= prevMinutes) {
                    revertTimeField(binder, timeField)
                    Toast.makeText(
                        this,
                        getString(R.string.recording_time_not_increasing, rowIndex, rowIndex + 1),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
            }
        }

        binder.committedTimeValue = newText
        binder.row = binder.row.copy(timeValue = newText)
        persistRow(binder.row)
        recomputeAllDurations()
    }

    private fun revertTimeField(binder: RowBinding, timeField: EditText) {
        binder.isRevertingTimeText = true
        val revertTo = binder.committedTimeValue
        timeField.setText(revertTo)
        timeField.setSelection(revertTo.length)
        binder.isRevertingTimeText = false
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

    private fun attemptSave() {
        if (mode == RecordingMode.HISTORY && !isEditable) {
            finish()
            return
        }

        for ((index, binder) in rowBindings.withIndex()) {
            val rowNumber = index + 1
            if (binder.row.actionName.isBlank()) {
                Toast.makeText(this, getString(R.string.save_missing_action, rowNumber), Toast.LENGTH_LONG).show()
                focusCell(index, 0)
                return
            }
            if (timeEnabled && binder.row.timeValue.isBlank()) {
                Toast.makeText(this, getString(R.string.save_missing_time, rowNumber), Toast.LENGTH_LONG).show()
                focusCell(index, columnTypesForMode().indexOf(FieldType.TIME))
                return
            }
        }

        for (i in 0 until rowBindings.size - 1) {
            val current = rowBindings[i].row.actionName
            val next = rowBindings[i + 1].row.actionName
            if (current.isNotBlank() && current.equals(next, ignoreCase = true)) {
                Toast.makeText(
                    this,
                    getString(R.string.recording_consecutive_action_error, i + 1, i + 2),
                    Toast.LENGTH_LONG
                ).show()
                focusCell(i, 0)
                return
            }
        }

        if (timeEnabled) {
            var totalMinutes = 0
            for ((index, binder) in rowBindings.withIndex()) {
                val durationInt = binder.row.durationValue.toIntOrNull()
                if (durationInt == null) {
                    Toast.makeText(
                        this,
                        getString(R.string.recording_duration_incomplete, index + 1),
                        Toast.LENGTH_LONG
                    ).show()
                    focusCell(index, columnTypesForMode().indexOf(FieldType.TIME))
                    return
                }
                if (durationInt < 0) {
                    Toast.makeText(
                        this,
                        getString(R.string.recording_duration_negative, index + 1),
                        Toast.LENGTH_LONG
                    ).show()
                    focusCell(index, columnTypesForMode().indexOf(FieldType.TIME))
                    return
                }
                totalMinutes += durationInt
            }
            if (totalMinutes != 1440) {
                Toast.makeText(
                    this,
                    getString(R.string.recording_total_not_1440, totalMinutes),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        finish()
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