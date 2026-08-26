package com.dar.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SuperActionDao {

    @Insert
    suspend fun insert(superAction: SuperActionEntity): Long

    @Update
    suspend fun update(superAction: SuperActionEntity)

    @Insert
    suspend fun addGeneralToSuper(crossRef: SuperActionGeneralCrossRef)

    @Delete
    suspend fun removeGeneralFromSuper(crossRef: SuperActionGeneralCrossRef)

    @Query("DELETE FROM super_action_general_cross_ref WHERE superActionId = :superActionId")
    suspend fun clearGeneralsForSuper(superActionId: Long)

    @Query("SELECT * FROM super_action WHERE dslaId = :dslaId ORDER BY id ASC")
    fun getAllForDsla(dslaId: Long): Flow<List<SuperActionEntity>>

    @Query("SELECT * FROM super_action WHERE dslaId = :dslaId AND endDate IS NULL ORDER BY id ASC")
    fun getActiveForDsla(dslaId: Long): Flow<List<SuperActionEntity>>

    @Query("SELECT * FROM super_action WHERE dslaId = :dslaId AND endDate IS NOT NULL ORDER BY id ASC")
    fun getDeletedForDsla(dslaId: Long): Flow<List<SuperActionEntity>>

    @Query("""
        SELECT general_action.* FROM general_action
        INNER JOIN super_action_general_cross_ref AS ref
        ON general_action.id = ref.generalActionId
        WHERE ref.superActionId = :superActionId
    """)
    fun getGeneralActionsInSuper(superActionId: Long): Flow<List<GeneralActionEntity>>

    @Query("UPDATE super_action SET endDate = :today, deletedDate = :today WHERE id = :superActionId")
    suspend fun softDelete(superActionId: Long, today: String)

    @Query("UPDATE super_action SET endDate = NULL, recoveredDate = :today WHERE id = :superActionId")
    suspend fun recover(superActionId: Long, today: String)
}
