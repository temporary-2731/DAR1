package com.dar.app

import kotlin.math.floor

private const val MINUTES_PER_DAY = 24 * 60

fun RecordingActivity.recomputeAllDurations() {
    if (!timeEnabled) return

    var total = 0
    var allComplete = true

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
            if (currentMinutes != null) {
                (MINUTES_PER_DAY - currentMinutes).toString()
            } else {
                ""
            }
        }

        current.row = current.row.copy(durationValue = durationText)
        current.durationView?.text = durationText.ifEmpty { getString(R.string.recording_duration_pending) }
        persistRow(current.row)

        val durationInt = durationText.toIntOrNull()
        if (durationInt != null) {
            total += durationInt
        } else {
            allComplete = false
        }
    }

    binding.totalDurationText.text = if (allComplete) {
        getString(R.string.recording_total_duration_format, total)
    } else {
        getString(R.string.recording_total_duration_format, total) + " (incomplete)"
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