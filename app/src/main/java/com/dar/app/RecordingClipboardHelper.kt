package com.dar.app

import android.graphics.Color
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.max
import kotlin.math.min

// ---------- Selection mode ----------

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
    anchorRow = row
    anchorCol = col
    extentRow = row
    extentCol = col
    setFieldsFocusable(false)
    binding.selectionToolbar.visibility = android.view.View.VISIBLE
    updateSelectionHighlight()
    Toast.makeText(this, R.string.selection_hint, Toast.LENGTH_SHORT).show()
}

fun RecordingActivity.extendSelection(row: Int, col: Int) {
    extentRow = row
    extentCol = col
    updateSelectionHighlight()
}

fun RecordingActivity.endSelection() {
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
    endSelection()
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
    endSelection()
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

// ---------- Cell menu (single cell) ----------

fun RecordingActivity.showCellMenu(binder: RowBinding, fieldType: FieldType, rowIndex: Int) {
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

fun RecordingActivity.showRowMenu(binder: RowBinding) {
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
