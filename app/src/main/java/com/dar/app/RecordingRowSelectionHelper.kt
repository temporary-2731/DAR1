package com.dar.app

import android.graphics.Color
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.RecordingRow
import kotlinx.coroutines.launch

fun RecordingActivity.highlightRow(rowIndex: Int, highlighted: Boolean) {
    val binder = rowBindings.getOrNull(rowIndex) ?: return
    val color = if (highlighted) RECORDING_HIGHLIGHT_COLOR else Color.TRANSPARENT
    binder.rowLabel.setBackgroundColor(color)
    binder.actionField.setBackgroundColor(color)
    binder.timeField?.setBackgroundColor(color)
    for (q in binder.quanFields) q.setBackgroundColor(color)
    binder.commentField.setBackgroundColor(color)
}

fun RecordingActivity.startRowSelection(initialRowIndex: Int) {
    rowSelectionActive = true
    rowSelectionNeedsNewAnchor = false
    selectedRowIndices.clear()
    selectedRowIndices.add(initialRowIndex)
    setFieldsFocusable(false)
    binding.rowSelectionToolbar.visibility = android.view.View.VISIBLE
    highlightRow(initialRowIndex, true)
    Toast.makeText(this, R.string.row_selection_hint, Toast.LENGTH_SHORT).show()
}

fun RecordingActivity.toggleRowSelection(rowIndex: Int) {
    if (selectedRowIndices.contains(rowIndex)) {
        selectedRowIndices.remove(rowIndex)
        highlightRow(rowIndex, false)
    } else {
        selectedRowIndices.add(rowIndex)
        highlightRow(rowIndex, true)
    }
}

fun RecordingActivity.setRowPasteAnchor(rowIndex: Int) {
    for (idx in selectedRowIndices) highlightRow(idx, false)
    selectedRowIndices.clear()
    selectedRowIndices.add(rowIndex)
    rowSelectionNeedsNewAnchor = false
    highlightRow(rowIndex, true)
}

fun RecordingActivity.endRowSelection() {
    if (!rowSelectionActive) return
    rowSelectionActive = false
    rowSelectionNeedsNewAnchor = false
    for (idx in selectedRowIndices) highlightRow(idx, false)
    selectedRowIndices.clear()
    setFieldsFocusable(true)
    binding.rowSelectionToolbar.visibility = android.view.View.GONE
}

fun RecordingActivity.copyRowSelection() {
    val sortedIndices = selectedRowIndices.sorted()
    if (sortedIndices.isEmpty()) return
    val base = sortedIndices.first()
    val snapshots = sortedIndices.map { idx ->
        val b = rowBindings[idx]
        RowSnapshot(idx - base, b.row.actionName, b.row.timeValue, b.row.quan1, b.row.quan2, b.row.quan3, b.row.comment)
    }
    clipboard = ClipboardContent.RowBlock(snapshots)
    Toast.makeText(this, R.string.recording_copied, Toast.LENGTH_SHORT).show()
    for (idx in selectedRowIndices) highlightRow(idx, false)
    selectedRowIndices.clear()
    rowSelectionNeedsNewAnchor = true
}

fun RecordingActivity.cutRowSelection() {
    val sortedIndices = selectedRowIndices.sorted()
    if (sortedIndices.isEmpty()) return
    val base = sortedIndices.first()
    val snapshots = sortedIndices.map { idx ->
        val b = rowBindings[idx]
        RowSnapshot(idx - base, b.row.actionName, b.row.timeValue, b.row.quan1, b.row.quan2, b.row.quan3, b.row.comment)
    }
    clipboard = ClipboardContent.RowBlock(snapshots)
    Toast.makeText(this, R.string.recording_cut, Toast.LENGTH_SHORT).show()
    removeRowsAndAwaitPaste(sortedIndices)
}

fun RecordingActivity.deleteRowSelection() {
    for (idx in selectedRowIndices) {
        clearRow(rowBindings[idx])
    }
    endRowSelection()
}

/** Physically removes the selected rows, renumbers what's left, then keeps row-selection mode open awaiting a paste anchor. */
fun RecordingActivity.removeRowsAndAwaitPaste(sortedIndices: List<Int>) {
    lifecycleScope.launch {
        val rowsToRemove = sortedIndices.map { rowBindings[it].row }
        for (r in rowsToRemove) {
            db.recordingDao().delete(r)
        }
        val remaining = db.recordingDao().getRowsForDate(dslaId, todayDate).sortedBy { it.rowNumber }
        for ((i, r) in remaining.withIndex()) {
            if (r.rowNumber != i + 1) db.recordingDao().update(r.copy(rowNumber = i + 1))
        }
        var updatedRows = db.recordingDao().getRowsForDate(dslaId, todayDate)
        if (updatedRows.isEmpty()) {
            db.recordingDao().insert(RecordingRow(dslaId = dslaId, date = todayDate, rowNumber = 1))
            updatedRows = db.recordingDao().getRowsForDate(dslaId, todayDate)
        }
        renderRows(updatedRows)
        rowSelectionActive = true
        rowSelectionNeedsNewAnchor = true
        selectedRowIndices.clear()
        setFieldsFocusable(false)
        binding.rowSelectionToolbar.visibility = android.view.View.VISIBLE
    }
}

fun RecordingActivity.pasteRowSelection() {
    val clip = clipboard
    if (clip !is ClipboardContent.RowBlock) {
        endRowSelection()
        return
    }
    val base = selectedRowIndices.minOrNull()
    if (base == null) {
        endRowSelection()
        return
    }
    val maxOffset = clip.rows.maxOfOrNull { it.offset } ?: 0
    val neededRowCount = base + maxOffset + 1

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
            completeRowPaste(clip, base)
        }
    } else {
        completeRowPaste(clip, base)
    }
}

fun RecordingActivity.completeRowPaste(clip: ClipboardContent.RowBlock, base: Int) {
    for (snapshot in clip.rows) {
        val targetIndex = base + snapshot.offset
        if (targetIndex !in rowBindings.indices) continue
        val binder = rowBindings[targetIndex]
        applyRowClipboard(
            binder,
            ClipboardContent.Row(
                actionName = snapshot.actionName,
                timeValue = snapshot.timeValue,
                quan1 = snapshot.quan1,
                quan2 = snapshot.quan2,
                quan3 = snapshot.quan3,
                comment = snapshot.comment
            )
        )
    }
    endRowSelection()
}
