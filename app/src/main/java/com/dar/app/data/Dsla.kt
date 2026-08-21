package com.dar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dsla")
data class Dsla(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val timeEnabled: Boolean = true
)
