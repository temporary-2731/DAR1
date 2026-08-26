package com.dar.app

import android.graphics.Color
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.RecordingRow
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

// ---------- Selection mode (single/multi cell) ----------

fun RecordingActivity.setFieldsFocusable(focusable: Boolean) {
    for (row in fieldMatrix) {
        for (field in row) {
            field.isFocusable = focusable
            field.isFocusableInTouchMode = focusable
        }
    }
}

fun RecordingActivity.startSelection(row: Int, col: Int) {
    selectionActive = true
    needsNewAnchor = false
    anchorRow = row
    anchorCol = col
    extentRow = row
    extentCol = col
    setFieldsFocusable(false)
    binding.selectionToolbar.visibility = android.view.View.VISIBLE
    updateSelectionHighlight()
    Toast.makeText(this, R.string.selection_hint, Toast.LENGTH_SHORT).show()
}

fun RecordingActivity.beginNewAnchor(row: Int, col: Int) {
    for (field in highlightedFields) {
        field.setBackgroundColor(Color.TRANSPARENT)
    }
    highlightedFields.clear()
    anchorRow = row
    anchorCol = col
    extentRow = row
    extentCol = col
    needsNewAnchor = false
    updateSelectionHighlight()
}

fun RecordingActivity.extendSelection(row: Int, col: Int) {
    extentRow = row
    extentCol = col
    updateSelectionHighlight()
}

fun RecordingActivity.keepSelectionAwaitingPaste() {
    for (field in highlightedFields) {
        field.setBackgroundColor(Color.TRANSPARENT)
    }
    highlightedFields.clear()
    needsNewAnchor = true
}

fun RecordingActivity.endSelection() {
    if (!selectionActive) return
    selectionActive = false
    needsNewAnchor = false
    for (field in highlightedFields) {
        field.setBackgroundColor(Color.TRANSPARENT)
    }
    highlightedFields.clear()
    anchorRow = -1; anchorCol = -1; extentRow = -1; extentCol = -1
    setFieldsFocusable(true)
    binding.selectionToolbar.visibility = android.view.View.GONE
}

fun RecordingActivity.selectionBounds(): IntArray {
    val minRow = min(anchorRow, extentRow)
    val maxRow = max(anchorRow, extentRow)
    val minCol = min(anchorCol, extentCol)
    val maxCol = max(anchorCol, extentCol)
    return intArrayOf(minRow, maxRow, minCol, maxCol)
}

fun RecordingActivity.updateSelectionHighlight() {
    for (field in highlightedFields) {
        field.setBackgroundColor(Color.TRANSPARENT)
    }
    highlightedFields.clear()

    val bounds = selectionBounds()
    val minRow = bounds[0]
    val maxRow = bounds[1]
    val minCol = bounds[2]
    val maxCol = bounds[3]
    for (r in minRow..maxRow) {
        val rowFields = fieldMatrix.getOrNull(r) ?: continue
        for (c in minCol..maxCol) {
            val field = rowFields.getOrNull(c) ?: continue
            field.setBackgroundColor(RECORDING_HIGHLIGHT_COLOR)
            highlightedFields.add(field)
        }
    }
}

fun RecordingActivity.copySelection() {
    val bounds = selectionBounds()
    val minRow = bounds[0]
    val maxRow = bounds[1]
    val minCol = bounds[2]
    val maxCol = bounds[3]
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
    keepSelectionAwaitingPaste()
}

fun RecordingActivity.cutSelection() {
    val bounds = selectionBounds()
    val minRow = bounds[0]
    val maxRow = bounds[1]
    val minCol = bounds[2]
    val maxCol = bounds[3]
    copySelectionWithoutEnding()
    val colTypes = columnTypesForMode()
    val rowSpan = maxRow - minRow + 1
    for (c in minCol..maxCol) {
        val fieldType = colTypes.getOrNull(c) ?: continue
        shiftColumnUp(fieldType, minRow, rowSpan)
    }
    Toast.makeText(this, R.string.recording_cut, Toast.LENGTH_SHORT).show()
    keepSelectionAwaitingPaste()
}

fun RecordingActivity.copySelectionWithoutEnding() {
    val bounds = selectionBounds()
    val minRow = bounds[0]
    val maxRow = bounds[1]
    val minCol = bounds[2]
    val maxCol = bounds[3]
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

fun RecordingActivity.deleteSelection() {
    val bounds = selectionBounds()
    val minRow = bounds[0]
    val maxRow = bounds[1]
    val minCol = bounds[2]
    val maxCol = bounds[3]
    val colTypes = columnTypesForMode()
    val rowSpan = maxRow - minRow + 1
    for (c in minCol..maxCol) {
        val fieldType = colTypes.getOrNull(c) ?: continue
        shiftColumnUp(fieldType, minRow, rowSpan)
    }
    endSelection()
}

fun RecordingActivity.pasteSelection() {
    val clip = clipboard
    if (clip !is ClipboardContent.Multi) {
        endSelection()
        return
    }
    val bounds = selectionBounds()
    val minRow = bounds[0]
    val minCol = bounds[2]
    val maxRowOffset = clip.cells.maxOfOrNull { it.rowOffset } ?: 0
    val neededRowCount = minRow + maxRowOffset + 1

    if (neededRowCount > rowBindings.size) {
        val rowsToAdd = neededRowCount - rowBindings.size
        lifecycleScope.launch {
            repeat(rowsToAdd) {
                val currentCount = db.recordingDao().countForDate(dslaId, todayDate)
                db.recordingDao().insert(
                    RecordingRow(dslaId = dslaId, date = todayDate, rowNumber = currentCount + 1)
                )
            }
            val rows = db.recordingDao().getRowsForDate(dslaId, todayDate)
            renderRows(rows)
            completePaste(clip, minRow, minCol)
        }
    } else {
        completePaste(clip, minRow, minCol)
    }
}

fun RecordingActivity.completePaste(clip: ClipboardContent.Multi, minRow: Int, minCol: Int) {
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

// ---------- Cell value access ----------

fun RecordingActivity.getFieldValue(binder: RowBinding, fieldType: FieldType): String = when (fieldType) {
    FieldType.ACTION -> binder.row.actionName
    FieldType.TIME -> binder.row.timeValue
    FieldType.QUAN1 -> binder.row.quan1
    FieldType.QUAN2 -> binder.row.quan2
    FieldType.QUAN3 -> binder.row.quan3
    FieldType.COMMENT -> binder.row.comment
}

fun RecordingActivity.setFieldValue(binder: RowBinding, fieldType: FieldType, value: String) {
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

    val targetField = when (fieldType) {
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

fun RecordingActivity.shiftColumnUp(fieldType: FieldType, startRow: Int, count: Int) {
    val values = rowBindings.map { getFieldValue(it, fieldType) }.toMutableList()
    repeat(count) {
        if (startRow < values.size) values.removeAt(startRow)
    }
    while (values.size < rowBindings.size) values.add("")
    for (i in rowBindings.indices) {
        setFieldValue(rowBindings[i], fieldType, values[i])
    }
}

// ---------- Add Row / Add Cell (insert-and-shift) ----------

fun RecordingActivity.insertRowAfter(binder: RowBinding) {
    captureUndoSnapshot()
    lifecycleScope.launch {
        val insertPosition = binder.row.rowNumber
        val allRows = db.recordingDao().getRowsForDate(dslaId, todayDate)
        for (r in allRows) {
            if (r.rowNumber > insertPosition) {
                db.recordingDao().update(r.copy(rowNumber = r.rowNumber + 1))
            }
        }
        db.recordingDao().insert(
            RecordingRow(dslaId = dslaId, date = todayDate, rowNumber = insertPosition + 1)
        )
        val updatedRows = db.recordingDao().getRowsForDate(dslaId, todayDate)
        renderRows(updatedRows)
    }
}

fun RecordingActivity.insertCellAt(fieldType: FieldType, insertRowIndex: Int) {
    captureUndoSnapshot()
    val currentValues = rowBindings.map { getFieldValue(it, fieldType) }
    val lastValue = currentValues.lastOrNull() ?: ""
    val needsOverflowRow = lastValue.isNotEmpty()

    if (needsOverflowRow) {
        lifecycleScope.launch {
            val currentCount = db.recordingDao().countForDate(dslaId, todayDate)
            db.recordingDao().insert(
                RecordingRow(dslaId = dslaId, date = todayDate, rowNumber = currentCount + 1)
            )
            val rows = db.recordingDao().getRowsForDate(dslaId, todayDate)
            renderRows(rows)
            performColumnInsert(fieldType, insertRowIndex, currentValues)
        }
    } else {
        performColumnInsert(fieldType, insertRowIndex, currentValues)
    }
}

private fun RecordingActivity.performColumnInsert(
    fieldType: FieldType,
    insertRowIndex: Int,
    previousValues: List<String>
) {
    val values = previousValues.toMutableList()
    values.add(insertRowIndex, "")
    val trimmed = values.take(rowBindings.size)
    for (i in rowBindings.indices) {
        setFieldValue(rowBindings[i], fieldType, trimmed.getOrElse(i) { "" })
    }
}

/** Physically deletes a single row and renumbers everything below it up by one. */
fun RecordingActivity.removeRow(binder: RowBinding) {
    captureUndoSnapshot()
    lifecycleScope.launch {
        val removedNumber = binder.row.rowNumber
        db.recordingDao().delete(binder.row)
        val remaining = db.recordingDao().getRowsForDate(dslaId, todayDate)
        for (r in remaining) {
            if (r.rowNumber > removedNumber) {
                db.recordingDao().update(r.copy(rowNumber = r.rowNumber - 1))
            }
        }
        var updatedRows = db.recordingDao().getRowsForDate(dslaId, todayDate)
        if (updatedRows.isEmpty()) {
            db.recordingDao().insert(RecordingRow(dslaId = dslaId, date = todayDate, rowNumber = 1))
            updatedRows = db.recordingDao().getRowsForDate(dslaId, todayDate)
        }
        renderRows(updatedRows)
    }
}

// ---------- Cell menu (single cell) ----------

fun RecordingActivity.showCellMenu(binder: RowBinding, fieldType: FieldType, rowIndex: Int) {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cell_menu, null)
    val btnSelect = dialogView.findViewById<Button>(R.id.btn_select)
    val btnCopy = dialogView.findViewById<Button>(R.id.btn_copy)
    val btnCut = dialogView.findViewById<Button>(R.id.btn_cut)
    val btnDelete = dialogView.findViewById<Button>(R.id.btn_delete)
    val btnPaste = dialogView.findViewById<Button>(R.id.btn_paste)
    val btnAddCell = dialogView.findViewById<Button>(R.id.btn_add_cell)

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
        captureUndoSnapshot()
        clipboard = ClipboardContent.Cell(category, getFieldValue(binder, fieldType))
        shiftColumnUp(fieldType, rowIndex, 1)
        Toast.makeText(this, R.string.recording_cut, Toast.LENGTH_SHORT).show()
        sheet.dismiss()
    }
    btnDelete.setOnClickListener {
        captureUndoSnapshot()
        shiftColumnUp(fieldType, rowIndex, 1)
        sheet.dismiss()
    }
    btnPaste.setOnClickListener {
        val currentClip = clipboard
        if (currentClip is ClipboardContent.Cell && currentClip.category == category) {
            captureUndoSnapshot()
            setFieldValue(binder, fieldType, currentClip.value)
        }
        sheet.dismiss()
    }
    btnAddCell.setOnClickListener {
        insertCellAt(fieldType, rowIndex)
        sheet.dismiss()
    }

    sheet.show()
}

// ---------- Row menu (single row) ----------

fun RecordingActivity.rowToClipboard(binder: RowBinding): ClipboardContent.Row {
    return ClipboardContent.Row(
        actionName = binder.row.actionName,
        timeValue = binder.row.timeValue,
        quan1 = binder.row.quan1,
        quan2 = binder.row.quan2,
        quan3 = binder.row.quan3,
        comment = binder.row.comment
    )
}

fun RecordingActivity.clearRow(binder: RowBinding) {
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

fun RecordingActivity.applyRowClipboard(binder: RowBinding, clip: ClipboardContent.Row) {
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

fun RecordingActivity.refreshRowFieldsFromModel(binder: RowBinding) {
    binder.actionField.setText(binder.row.actionName)
    binder.timeField?.setText(binder.row.timeValue)
    binder.commentField.setText(binder.row.comment)
    binder.quanFields.getOrNull(0)?.setText(binder.row.quan1)
    binder.quanFields.getOrNull(1)?.setText(binder.row.quan2)
    binder.quanFields.getOrNull(2)?.setText(binder.row.quan3)
}

fun RecordingActivity.showRowMenu(binder: RowBinding, rowIndex: Int) {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_row_menu, null)
    val btnRowSelect = dialogView.findViewById<Button>(R.id.btn_row_select)
    val btnCopyRow = dialogView.findViewById<Button>(R.id.btn_copy_row)
    val btnCutRow = dialogView.findViewById<Button>(R.id.btn_cut_row)
    val btnDeleteRow = dialogView.findViewById<Button>(R.id.btn_delete_row)
    val btnRemoveRow = dialogView.findViewById<Button>(R.id.btn_remove_row)
    val btnPasteRow = dialogView.findViewById<Button>(R.id.btn_paste_row)
    val btnInsertRow = dialogView.findViewById<Button>(R.id.btn_insert_row)

    val canPaste = clipboard is ClipboardContent.Row
    btnPasteRow.isEnabled = canPaste
    btnPasteRow.alpha = if (canPaste) 1f else 0.4f

    val sheet = BottomSheetDialog(this)
    sheet.setContentView(dialogView)

    btnRowSelect.setOnClickListener {
        sheet.dismiss()
        startRowSelection(rowIndex)
    }
    btnCopyRow.setOnClickListener {
        clipboard = rowToClipboard(binder)
        Toast.makeText(this, R.string.recording_copied, Toast.LENGTH_SHORT).show()
        sheet.dismiss()
    }
    btnCutRow.setOnClickListener {
        captureUndoSnapshot()
        clipboard = rowToClipboard(binder)
        clearRow(binder)
        Toast.makeText(this, R.string.recording_cut, Toast.LENGTH_SHORT).show()
        sheet.dismiss()
    }
    btnDeleteRow.setOnClickListener {
        captureUndoSnapshot()
        clearRow(binder)
        sheet.dismiss()
    }
    btnRemoveRow.setOnClickListener {
        removeRow(binder)
        sheet.dismiss()
    }
    btnPasteRow.setOnClickListener {
        val clip = clipboard
        if (clip is ClipboardContent.Row) {
            captureUndoSnapshot()
            applyRowClipboard(binder, clip)
        }
        sheet.dismiss()
    }
    btnInsertRow.setOnClickListener {
        insertRowAfter(binder)
        sheet.dismiss()
    }

    sheet.show()
}
