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
    val startDate: String? = null,
    val endDate: String? = null,
    val usageFrequency: Int = 0,
    val createdDate: String = "",
    val deletedDate: String? = null,
    val recoveredDate: String? = null
)
