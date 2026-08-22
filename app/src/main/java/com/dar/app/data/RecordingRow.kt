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
    val quan1: String = "",    // single Quan column for this slice
    val comment: String = ""
)
