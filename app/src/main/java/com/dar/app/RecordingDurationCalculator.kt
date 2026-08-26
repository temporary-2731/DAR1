package com.dar.app

import kotlin.math.floor

private const val MINUTES_PER_DAY = 24 * 60

fun RecordingActivity.recomputeAllDurations() {
    if (!timeEnabled) return

    for (i in rowBindings.indices) {
        val current = rowBindings[i]
        val currentMinutes = parseTimeToMinutes(current.row.timeValue)

        val durationText: String = if (i < rowBindings.size - 1) {
            val nextMinutes = parseTimeToMinutes(rowBindings[i + 1].row.timeValue)
            if (currentMinutes != null && nextMinutes != null) {
                (nextMinutes - currentMinutes).toString()
            } else {
                ""
            }
        } else {
            // Final row: duration runs until midnight (24:00 minus this row's time).
            if (currentMinutes != null) {
                (MINUTES_PER_DAY - currentMinutes).toString()
            } else {
                ""
            }
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
