package com.dar.app

import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import com.dar.app.data.RecordingRow

const val RECORDING_HIGHLIGHT_COLOR = 0xFFFFE082.toInt()

enum class FieldType { ACTION, TIME, QUAN1, QUAN2, QUAN3, COMMENT }

enum class CellCategory { ACTION, COMMENT, VALUE }

data class CellSnapshot(
    val rowOffset: Int,
    val colOffset: Int,
    val category: CellCategory,
    val value: String
)

sealed class ClipboardContent {
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

class RowBinding(
    var row: RecordingRow,
    val actionField: AutoCompleteTextView,
    val timeField: EditText?,
    val durationView: TextView?,
    val quanFields: List<EditText>,
    val commentField: EditText,
    var committedActionName: String,
    var isRevertingActionText: Boolean = false
)

fun categoryOf(fieldType: FieldType): CellCategory = when (fieldType) {
    FieldType.ACTION -> CellCategory.ACTION
    FieldType.COMMENT -> CellCategory.COMMENT
    else -> CellCategory.VALUE
}
