package com.dar.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneralActionDao {

    @Insert
    suspend fun insert(generalAction: GeneralActionEntity): Long

    @Update
    suspend fun update(generalAction: GeneralActionEntity)

    @Insert
    suspend fun addActionToGeneral(crossRef: GeneralActionActionCrossRef)

    @Delete
    suspend fun removeActionFromGeneral(crossRef: GeneralActionActionCrossRef)

    @Query("DELETE FROM general_action_action_cross_ref WHERE generalActionId = :generalActionId")
    suspend fun clearActionsForGeneral(generalActionId: Long)

    @Query("SELECT * FROM general_action WHERE dslaId = :dslaId ORDER BY id ASC")
    fun getAllForDsla(dslaId: Long): Flow<List<GeneralActionEntity>>

    @Query("SELECT * FROM general_action WHERE dslaId = :dslaId AND endDate IS NULL ORDER BY id ASC")
    fun getActiveForDsla(dslaId: Long): Flow<List<GeneralActionEntity>>

    @Query("SELECT * FROM general_action WHERE dslaId = :dslaId AND endDate IS NOT NULL ORDER BY id ASC")
    fun getDeletedForDsla(dslaId: Long): Flow<List<GeneralActionEntity>>

    @Query("""
        SELECT action.* FROM action
        INNER JOIN general_action_action_cross_ref AS ref
        ON action.id = ref.actionId
        WHERE ref.generalActionId = :generalActionId
    """)
    fun getActionsInGeneral(generalActionId: Long): Flow<List<ActionEntity>>

    @Query("UPDATE general_action SET endDate = :today, deletedDate = :today WHERE id = :generalActionId")
    suspend fun softDelete(generalActionId: Long, today: String)

    @Query("UPDATE general_action SET endDate = NULL, recoveredDate = :today WHERE id = :generalActionId")
    suspend fun recover(generalActionId: Long, today: String)
}
