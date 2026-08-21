package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "general_action")
data class GeneralActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dslaId: Long,
    val description: String = "",
    val startDate: String? = null,
    val endDate: String? = null
)
