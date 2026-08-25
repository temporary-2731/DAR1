package com.dar.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private enum class FieldType { ACTION, TIME, QUAN1, QUAN2, QUAN3, COMMENT }

private enum class CellCategory { ACTION, COMMENT, VALUE }

private data class CellSnapshot(
    val rowOffset: Int,
    val colOffset: Int,
    val category: CellCategory,
    val value: String
)

private sealed class ClipboardContent {
    data class Cell(val category: CellCategory, val value: String) : ClipboardContent()
    data class Row(
        val actionName: String,
        val timeValue: String,
        val quan1: String,
        val quan2: String,
        val quan3: String,
        val comment: String
    ) : ClipboardContent()
    data class Multi(val cells: List<CellSnapshot>) : ClipboardContent()
}

class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L
    private lateinit var todayDate: String
    private var timeEnabled: Boolean = true

    private var libraryActions: List<ActionEntity> = emptyList()
    private lateinit var actionNameAdapter: ArrayAdapter<String>
    private var clipboard: ClipboardContent? = null

    private class RowBinding(
        var row: RecordingRow,
        val actionField: AutoCompleteTextView,
        val timeField: EditText?,
        val durationView: TextView?,
        val quanFields: List<EditText>,
        val commentField: EditText,
        var committedActionName: String,
        var isRevertingActionText: Boolean = false
    )

    private val rowBindings = mutableListOf<RowBinding>()
    private val fieldMatrix = mutableListOf<MutableList<EditText>>()
    private var currentRow = 0
    private var currentCol = 0

    // ---- Multi-cell selection state ----
    private var selectionActive = false
    private var anchorRow = -1
    private var anchorCol = -1
    private var extentRow = -1
    private var extentCol = -1
    private val highlightedFields = mutableListOf<EditText>()

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
        private const val HIGHLIGHT_COLOR = 0xFFFFE082.toInt()
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

        binding.btnSelCopy.setOnClickListener { copySelection() }
        binding.btnSelCut.setOnClickListener { cutSelection() }
        binding.btnSelDelete.setOnClickListener { deleteSelection() }
        binding.btnSelPaste.setOnClickListener { pasteSelection() }
        binding.btnSelDone.setOnClickListener { endSelection() }

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

    private fun columnTypesForMode(): List<FieldType> {
        return if (timeEnabled) {
            listOf(FieldType.ACTION, FieldType.TIME, FieldType.QUAN1, FieldType.COMMENT)
        } else {
            listOf(FieldType.ACTION, FieldType.QUAN1, FieldType.QUAN2, FieldType.QUAN3, FieldType.COMMENT)
        }
    }

    private fun renderRows(rows: List<RecordingRow>) {
        endSelection()
        binding.rowContainer.removeAllViews()
        rowBindings.clear()

        addHeaderView()
        for (row in rows) {
            addRowView(row)
        }
        recomputeAllDurations()
        buildFieldMatrix()
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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
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

        rowLabel.isLongClickable = true
        rowLabel.setOnLongClickListener {
            showRowMenu(binder)
            true
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

    // ---------- Field matrix + selection click wiring ----------

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
                        extendSelection(rowIndex, colIndex)
                    }
                }
            }
        }
    }

    // ---------- Selection mode ----------

    private fun setFieldsFocusable(focusable: Boolean) {
        for (row in fieldMatrix) {
            for (field in row) {
                field.isFocusable = focusable
                field.isFocusableInTouchMode = focusable
            }
        }
    }

    private fun startSelection(row: Int, col: Int) {
        selectionActive = true
        anchorRow = row
        anchorCol = col
        extentRow = row
        extentCol = col
        setFieldsFocusable(false)
        binding.selectionToolbar.visibility = android.view.View.VISIBLE
        updateSelectionHighlight()
        Toast.makeText(this, R.string.selection_hint, Toast.LENGTH_SHORT).show()
    }

    private fun extendSelection(row: Int, col: Int) {
        extentRow = row
        extentCol = col
        updateSelectionHighlight()
    }

    private fun endSelection() {
        if (!selectionActive) return
        selectionActive = false
        for (field in highlightedFields) {
            field.setBackgroundColor(Color.TRANSPARENT)
        }
        highlightedFields.clear()
        anchorRow = -1; anchorCol = -1; extentRow = -1; extentCol = -1
        setFieldsFocusable(true)
        binding.selectionToolbar.visibility = android.view.View.GONE
    }

    private fun selectionBounds(): IntArray {
        val minRow = min(anchorRow, extentRow)
        val maxRow = max(anchorRow, extentRow)
        val minCol = min(anchorCol, extentCol)
        val maxCol = max(anchorCol, extentCol)
        return intArrayOf(minRow, maxRow, minCol, maxCol)
    }

    private fun updateSelectionHighlight() {
        for (field in highlightedFields) {
            field.setBackgroundColor(Color.TRANSPARENT)
        }
        highlightedFields.clear()

        val (minRow, maxRow, minCol, maxCol) = selectionBounds().toList()
        for (r in minRow..maxRow) {
            val rowFields = fieldMatrix.getOrNull(r) ?: continue
            for (c in minCol..maxCol) {
                val field = rowFields.getOrNull(c) ?: continue
                field.setBackgroundColor(HIGHLIGHT_COLOR)
                highlightedFields.add(field)
            }
        }
    }

    private fun categoryOf(fieldType: FieldType): CellCategory = when (fieldType) {
        FieldType.ACTION -> CellCategory.ACTION
        FieldType.COMMENT -> CellCategory.COMMENT
        else -> CellCategory.VALUE
    }

    private fun copySelection() {
        val (minRow, maxRow, minCol, maxCol) = selectionBounds().toList()
        val colTypes = columnTypesForMode()
        val snapshots = mutableListOf<CellSnapshot>()
        for (r in minRow..maxRow) {
            for (c in minCol..maxCol) {
                val fieldType = colTypes.getOrNull(c) ?: continue
                val value = getFieldValue(rowBindings[r], fieldType)
                snapshots.add(CellSnapshot(r - minRow, c - minCol, categoryOf(fieldType), value))
            }
        }
        clipboard = ClipboardContent.Multi(snapshots)
        Toast.makeText(this, R.string.recording_copied, Toast.LENGTH_SHORT).show()
        endSelection()
    }

    private fun cutSelection() {
        val (minRow, maxRow, minCol, maxCol) = selectionBounds().toList()
        copySelectionWithoutEnding()
        val colTypes = columnTypesForMode()
        val rowSpan = maxRow - minRow + 1
        for (c in minCol..maxCol) {
            val fieldType = colTypes.getOrNull(c) ?: continue
            shiftColumnUp(fieldType, minRow, rowSpan)
        }
        Toast.makeText(this, R.string.recording_cut, Toast.LENGTH_SHORT).show()
        endSelection()
    }

    private fun copySelectionWithoutEnding() {
        val (minRow, maxRow, minCol, maxCol) = selectionBounds().toList()
        val colTypes = columnTypesForMode()
        val snapshots = mutableListOf<CellSnapshot>()
        for (r in minRow..maxRow) {
            for (c in minCol..maxCol) {
                val fieldType = colTypes.getOrNull(c) ?: continue
                val value = getFieldValue(rowBindings[r], fieldType)
                snapshots.add(CellSnapshot(r - minRow, c - minCol, categoryOf(fieldType), value))
            }
        }
        clipboard = ClipboardContent.Multi(snapshots)
    }

    private fun deleteSelection() {
        val (minRow, maxRow, minCol, maxCol) = selectionBounds().toList()
        val colTypes = columnTypesForMode()
        val rowSpan = maxRow - minRow + 1
        for (c in minCol..maxCol) {
            val fieldType = colTypes.getOrNull(c) ?: continue
            shiftColumnUp(fieldType, minRow, rowSpan)
        }
        endSelection()
    }

    private fun pasteSelection() {
        val clip = clipboard
        if (clip !is ClipboardContent.Multi) {
            endSelection()
            return
        }
        val (minRow, _, minCol, _) = selectionBounds().toList()
        val colTypes = columnTypesForMode()
        var skipped = 0

        for (snapshot in clip.cells) {
            val targetRow = minRow + snapshot.rowOffset
            val targetCol = minCol + snapshot.colOffset
            if (targetRow !in rowBindings.indices || targetCol !in colTypes.indices) {
                skipped++
                continue
            }
            val targetFieldType = colTypes[targetCol]
            if (categoryOf(targetFieldType) != snapshot.category) {
                skipped++
                continue
            }
            setFieldValue(rowBindings[targetRow], targetFieldType, snapshot.value)
        }

        if (skipped > 0) {
            Toast.makeText(this, R.string.selection_paste_skipped, Toast.LENGTH_SHORT).show()
        }
        endSelection()
    }

    // ---------- Cell menu (single cell) ----------

    private fun getFieldValue(binder: RowBinding, fieldType: FieldType): String = when (fieldType) {
        FieldType.ACTION -> binder.row.actionName
        FieldType.TIME -> binder.row.timeValue
        FieldType.QUAN1 -> binder.row.quan1
        FieldType.QUAN2 -> binder.row.quan2
        FieldType.QUAN3 -> binder.row.quan3
        FieldType.COMMENT -> binder.row.comment
    }

    private fun setFieldValue(binder: RowBinding, fieldType: FieldType, value: String) {
        binder.row = when (fieldType) {
            FieldType.ACTION -> binder.row.copy(actionName = value)
            FieldType.TIME -> binder.row.copy(timeValue = value)
            FieldType.QUAN1 -> binder.row.copy(quan1 = value)
            FieldType.QUAN2 -> binder.row.copy(quan2 = value)
            FieldType.QUAN3 -> binder.row.copy(quan3 = value)
            FieldType.COMMENT -> binder.row.copy(comment = value)
        }
        persistRow(binder.row)

        if (fieldType == FieldType.ACTION) {
            binder.committedActionName = value
        }

        val targetField: EditText? = when (fieldType) {
            FieldType.ACTION -> binder.actionField
            FieldType.TIME -> binder.timeField
            FieldType.QUAN1 -> binder.quanFields.getOrNull(0)
            FieldType.QUAN2 -> binder.quanFields.getOrNull(1)
            FieldType.QUAN3 -> binder.quanFields.getOrNull(2)
            FieldType.COMMENT -> binder.commentField
        }
        targetField?.setText(value)

        if (fieldType == FieldType.TIME) {
            recomputeAllDurations()
        }
    }

    /** Removes [count] values starting at [startRow] in the given column and shifts everything below up. */
    private fun shiftColumnUp(fieldType: FieldType, startRow: Int, count: Int) {
        val values = rowBindings.map { getFieldValue(it, fieldType) }.toMutableList()
        repeat(count) {
            if (startRow < values.size) values.removeAt(startRow)
        }
        while (values.size < rowBindings.size) values.add("")
        for (i in rowBindings.indices) {
            setFieldValue(rowBindings[i], fieldType, values[i])
        }
    }

    private fun showCellMenu(binder: RowBinding, fieldType: FieldType, rowIndex: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cell_menu, null)
        val btnSelect = dialogView.findViewById<Button>(R.id.btn_select)
        val btnCopy = dialogView.findViewById<Button>(R.id.btn_copy)
        val btnCut = dialogView.findViewById<Button>(R.id.btn_cut)
        val btnDelete = dialogView.findViewById<Button>(R.id.btn_delete)
        val btnPaste = dialogView.findViewById<Button>(R.id.btn_paste)

        val category = categoryOf(fieldType)
        val clip = clipboard
        val canPaste = clip is ClipboardContent.Cell && clip.category == category
        btnPaste.isEnabled = canPaste
        btnPaste.alpha = if (canPaste) 1f else 0.4f

        val sheet = BottomSheetDialog(this)
        sheet.setContentView(dialogView)

        val colIndex = columnTypesForMode().indexOf(fieldType)

        btnSelect.setOnClickListener {
            sheet.dismiss()
            startSelection(rowIndex, colIndex)
        }
        btnCopy.setOnClickListener {
            clipboard = ClipboardContent.Cell(category, getFieldValue(binder, fieldType))
            Toast.makeText(this, R.string.recording_copied, Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }
        btnCut.setOnClickListener {
            clipboard = ClipboardContent.Cell(category, getFieldValue(binder, fieldType))
            shiftColumnUp(fieldType, rowIndex, 1)
            Toast.makeText(this, R.string.recording_cut, Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }
        btnDelete.setOnClickListener {
            shiftColumnUp(fieldType, rowIndex, 1)
            sheet.dismiss()
        }
        btnPaste.setOnClickListener {
            val currentClip = clipboard
            if (currentClip is ClipboardContent.Cell && currentClip.category == category) {
                setFieldValue(binder, fieldType, currentClip.value)
            }
            sheet.dismiss()
        }

        sheet.show()
    }

    // ---------- Row menu ----------

    private fun rowToClipboard(binder: RowBinding): ClipboardContent.Row {
        return ClipboardContent.Row(
            actionName = binder.row.actionName,
            timeValue = binder.row.timeValue,
            quan1 = binder.row.quan1,
            quan2 = binder.row.quan2,
            quan3 = binder.row.quan3,
            comment = binder.row.comment
        )
    }

    private fun clearRow(binder: RowBinding) {
        binder.row = binder.row.copy(
            actionName = "",
            timeValue = "",
            quan1 = "",
            quan2 = "",
            quan3 = "",
            comment = ""
        )
        binder.committedActionName = ""
        persistRow(binder.row)
        refreshRowFieldsFromModel(binder)
        recomputeAllDurations()
    }

    private fun applyRowClipboard(binder: RowBinding, clip: ClipboardContent.Row) {
        binder.row = binder.row.copy(
            actionName = clip.actionName,
            timeValue = clip.timeValue,
            quan1 = clip.quan1,
            quan2 = clip.quan2,
            quan3 = clip.quan3,
            comment = clip.comment
        )
        binder.committedActionName = clip.actionName
        persistRow(binder.row)
        refreshRowFieldsFromModel(binder)
        recomputeAllDurations()
    }

    private fun refreshRowFieldsFromModel(binder: RowBinding) {
        binder.actionField.setText(binder.row.actionName)
        binder.timeField?.setText(binder.row.timeValue)
        binder.commentField.setText(binder.row.comment)
        binder.quanFields.getOrNull(0)?.setText(binder.row.quan1)
        binder.quanFields.getOrNull(1)?.setText(binder.row.quan2)
        binder.quanFields.getOrNull(2)?.setText(binder.row.quan3)
    }

    private fun showRowMenu(binder: RowBinding) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_row_menu, null)
        val btnCopyRow = dialogView.findViewById<Button>(R.id.btn_copy_row)
        val btnCutRow = dialogView.findViewById<Button>(R.id.btn_cut_row)
        val btnDeleteRow = dialogView.findViewById<Button>(R.id.btn_delete_row)
        val btnPasteRow = dialogView.findViewById<Button>(R.id.btn_paste_row)

        val canPaste = clipboard is ClipboardContent.Row
        btnPasteRow.isEnabled = canPaste
        btnPasteRow.alpha = if (canPaste) 1f else 0.4f

        val sheet = BottomSheetDialog(this)
        sheet.setContentView(dialogView)

        btnCopyRow.setOnClickListener {
            clipboard = rowToClipboard(binder)
            Toast.makeText(this, R.string.recording_copied, Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }
        btnCutRow.setOnClickListener {
            clipboard = rowToClipboard(binder)
            clearRow(binder)
            Toast.makeText(this, R.string.recording_cut, Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }
        btnDeleteRow.setOnClickListener {
            clearRow(binder)
            sheet.dismiss()
        }
        btnPasteRow.setOnClickListener {
            val clip = clipboard
            if (clip is ClipboardContent.Row) {
                applyRowClipboard(binder, clip)
            }
            sheet.dismiss()
        }

        sheet.show()
    }

    // ---------- Navigation ----------

    private fun focusCell(row: Int, col: Int) {
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

    // ---------- Duration ----------

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

    // ---------- Persistence & misc ----------

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
