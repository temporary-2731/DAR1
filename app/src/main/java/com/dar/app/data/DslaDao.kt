package com.dar.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DslaDao {

    @Insert
    suspend fun insert(dsla: Dsla): Long

    @Update
    suspend fun update(dsla: Dsla)

    @Delete
    suspend fun delete(dsla: Dsla)

    @Query("SELECT * FROM dsla ORDER BY id ASC")
    fun getAll(): Flow<List<Dsla>>

    @Query("SELECT * FROM dsla WHERE id = :id")
    suspend fun getById(id: Long): Dsla?
}
