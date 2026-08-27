package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dsla")
data class Dsla(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val timeEnabled: Boolean = true,
    val beginDate: String = "",
    val endDate: String? = null,
    val analysisMode: String = "MODE1" // MODE1, MODE2, MANUAL
)
