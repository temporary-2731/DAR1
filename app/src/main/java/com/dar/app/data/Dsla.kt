package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dsla")
data class Dsla(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val timeEnabled: Boolean = true,
    val beginDate: String = "",   // DD/MM/YYYY, fixed at creation (defaults to creation day if left blank)
    val endDate: String? = null   // DD/MM/YYYY, null = ongoing indefinitely
)
