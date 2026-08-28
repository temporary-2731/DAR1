package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analysis_form")
data class AnalysisForm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dslaId: Long,
    val generalActionId: Long,
    val periodType: String, // DAILY, WEEKLY, MONTHLY, YEARLY, ALLTIME
    val beginDate: String,  // DD/MM/YYYY
    val endDate: String?    // null = ongoing
)
