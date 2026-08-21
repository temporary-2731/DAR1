package com.dar.app.data

import androidx.room.Entity

@Entity(
    tableName = "general_action_action_cross_ref",
    primaryKeys = ["generalActionId", "actionId"]
)
data class GeneralActionActionCrossRef(
    val generalActionId: Long,
    val actionId: Long
)
