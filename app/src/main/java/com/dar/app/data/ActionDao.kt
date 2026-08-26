package com.dar.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionDao {

    @Insert
    suspend fun insert(action: ActionEntity): Long

    @Update
    suspend fun update(action: ActionEntity)

    @Query("SELECT * FROM action WHERE dslaId = :dslaId ORDER BY id ASC")
    fun getAllForDsla(dslaId: Long): Flow<List<ActionEntity>>

    @Query("SELECT * FROM action WHERE dslaId = :dslaId AND endDate IS NULL ORDER BY id ASC")
    fun getActiveForDsla(dslaId: Long): Flow<List<ActionEntity>>

    @Query("SELECT * FROM action WHERE dslaId = :dslaId AND endDate IS NOT NULL ORDER BY id ASC")
    fun getDeletedForDsla(dslaId: Long): Flow<List<ActionEntity>>

    @Query("SELECT * FROM action WHERE dslaId = :dslaId AND endDate IS NULL ORDER BY usageFrequency DESC")
    fun getActiveSortedByFrequency(dslaId: Long): Flow<List<ActionEntity>>

    @Query("SELECT * FROM action WHERE dslaId = :dslaId AND name LIKE '%' || :query || '%' AND endDate IS NULL ORDER BY usageFrequency DESC")
    fun searchActive(dslaId: Long, query: String): Flow<List<ActionEntity>>

    @Query("UPDATE action SET endDate = :today, deletedDate = :today WHERE id = :actionId")
    suspend fun softDelete(actionId: Long, today: String)

    @Query("UPDATE action SET endDate = NULL, recoveredDate = :today WHERE id = :actionId")
    suspend fun recover(actionId: Long, today: String)

    @Query("UPDATE action SET usageFrequency = usageFrequency + 1 WHERE id = :actionId")
    suspend fun incrementUsage(actionId: Long)
}
