package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "super_action")
data class SuperActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dslaId: Long,
    val name: String,
    val description: String = "",
    val startDate: String? = null,
    val endDate: String? = null
)
