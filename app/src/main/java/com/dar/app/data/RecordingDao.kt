package com.dar.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Insert
    suspend fun insert(row: RecordingRow): Long

    @Update
    suspend fun update(row: RecordingRow)

    @Delete
    suspend fun delete(row: RecordingRow)

    @Query("SELECT * FROM recording_row WHERE dslaId = :dslaId AND date = :date ORDER BY rowNumber ASC")
    suspend fun getRowsForDate(dslaId: Long, date: String): List<RecordingRow>

    @Query("DELETE FROM recording_row WHERE dslaId = :dslaId AND date = :date")
    suspend fun deleteAllForDate(dslaId: Long, date: String)

    @Query("SELECT COUNT(*) FROM recording_row WHERE dslaId = :dslaId AND date = :date")
    suspend fun countForDate(dslaId: Long, date: String): Int

    @Query("SELECT DISTINCT date FROM recording_row WHERE dslaId = :dslaId")
    fun getDistinctDates(dslaId: Long): Flow<List<String>>

    @Query("SELECT * FROM recording_row WHERE dslaId = :dslaId")
    suspend fun getAllRowsForDsla(dslaId: Long): List<RecordingRow>
}
