package com.dar.app

import kotlin.math.floor

fun RecordingActivity.recomputeAllDurations() {
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

fun RecordingActivity.parseTimeToMinutes(value: String): Int? {
    if (value.isBlank()) return null
    val floatValue = value.toFloatOrNull() ?: return null
    if (floatValue < 0) return null
    val hours = floor(floatValue).toInt()
    val fractional = floatValue - hours
    val minutes = Math.round(fractional * 100f)
    if (minutes >= 60) return null
    return hours * 60 + minutes
}
