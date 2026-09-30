package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Duration/Quan1 parameter for Monthly/Yearly/All-time forms — a single scalar per
 *  Action, not a vector (Time is ignored for these period types per spec). */
@Entity(tableName = "analysis_form_scalar_param")
data class AnalysisFormScalarParam(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val formId: Long,
    val actionId: Long,
    val durationValue: Double? = null,
    val quan1Value: Double? = null
)
