package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action")
data class ActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dslaId: Long,
    val name: String,
    val description: String = "",
    val imagePath: String? = null,
    val startDate: String? = null,   // DD/MM/YYYY, null = from the beginning
    val endDate: String? = null,     // DD/MM/YYYY, null = no end / not deleted
    val usageFrequency: Int = 0
)
