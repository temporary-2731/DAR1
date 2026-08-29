package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analysis_form_action_param")
data class AnalysisFormActionParam(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val formId: Long,
    val actionId: Long,
    val weekday: Int = 1, // 1=Sunday .. 7=Saturday (Calendar.DAY_OF_WEEK convention)
    val dimension: Int = 1,
    val timeVector: String = "",
    val durationVector: String = "",
    val quan1Vector: String = ""
)
