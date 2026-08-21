package com.dar.app.data

import androidx.room.Entity

@Entity(
    tableName = "super_action_general_cross_ref",
    primaryKeys = ["superActionId", "generalActionId"]
)
data class SuperActionGeneralCrossRef(
    val superActionId: Long,
    val generalActionId: Long
)
