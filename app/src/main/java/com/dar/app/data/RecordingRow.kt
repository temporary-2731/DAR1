package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_row")
data class RecordingRow(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dslaId: Long,
    val date: String,          // DD/MM/YYYY
    val rowNumber: Int,        // R.n — order within this date
    val actionName: String = "",
    val timeValue: String = "",     // HH.MM, only used when time is enabled
    val durationValue: String = "", // calculated, never typed by the user
    val quan1: String = "",
    val quan2: String = "",         // only used when time is disabled
    val quan3: String = "",         // only used when time is disabled
    val comment: String = ""
)
