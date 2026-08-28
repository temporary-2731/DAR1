package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per Action inside a form, holding that Action's chosen vector dimension
 * and the entered parameter vectors as comma-separated strings (empty until filled).
 */
@Entity(tableName = "analysis_form_action_param")
data class AnalysisFormActionParam(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val formId: Long,
    val actionId: Long,
    val dimension: Int = 1,
    val timeVector: String = "",
    val durationVector: String = "",
    val quan1Vector: String = "",
    val quan2Vector: String = "",
    val quan3Vector: String = ""
)
