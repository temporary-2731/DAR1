package com.dar.app

import androidx.lifecycle.lifecycleScope
import com.dar.app.data.RecordingRow
import kotlinx.coroutines.launch

/** Captures the current state of every row for today and pushes it to the undo stack. Clears the redo stack. */
fun RecordingActivity.captureUndoSnapshot() {
    val snapshot = RecordingSnapshot(rowBindings.map { it.row.copy() })
    undoStack.addLast(snapshot)
    if (undoStack.size > UNDO_STACK_LIMIT) {
        undoStack.removeFirst()
    }
    redoStack.clear()
    updateUndoRedoButtons()
}

fun RecordingActivity.performUndo() {
    if (undoStack.isEmpty()) return
    val currentState = RecordingSnapshot(rowBindings.map { it.row.copy() })
    redoStack.addLast(currentState)
    val previousState = undoStack.removeLast()
    applySnapshot(previousState)
    updateUndoRedoButtons()
}

fun RecordingActivity.performRedo() {
    if (redoStack.isEmpty()) return
    val currentState = RecordingSnapshot(rowBindings.map { it.row.copy() })
    undoStack.addLast(currentState)
    val nextState = redoStack.removeLast()
    applySnapshot(nextState)
    updateUndoRedoButtons()
}

/** Replaces all of today's rows in the database with the given snapshot, then re-renders. */
private fun RecordingActivity.applySnapshot(snapshot: RecordingSnapshot) {
    suppressSnapshotCapture = true
    lifecycleScope.launch {
        db.recordingDao().deleteAllForDate(dslaId, todayDate)
        for (row in snapshot.rows) {
            db.recordingDao().insert(
                RecordingRow(
                    dslaId = row.dslaId,
                    date = row.date,
                    rowNumber = row.rowNumber,
                    actionName = row.actionName,
                    timeValue = row.timeValue,
                    durationValue = row.durationValue,
                    quan1 = row.quan1,
                    quan2 = row.quan2,
                    quan3 = row.quan3,
                    comment = row.comment
                )
            )
        }
        val rows = db.recordingDao().getRowsForDate(dslaId, todayDate)
        renderRows(rows)
        suppressSnapshotCapture = false
    }
}

fun RecordingActivity.updateUndoRedoButtons() {
    binding.btnUndo.isEnabled = undoStack.isNotEmpty()
    binding.btnUndo.alpha = if (undoStack.isNotEmpty()) 1f else 0.4f
    binding.btnRedo.isEnabled = redoStack.isNotEmpty()
    binding.btnRedo.alpha = if (redoStack.isNotEmpty()) 1f else 0.4f
}
