package com.dar.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisFormDao {

    @Insert
    suspend fun insertForm(form: AnalysisForm): Long

    @Update
    suspend fun updateForm(form: AnalysisForm)

    @Delete
    suspend fun deleteForm(form: AnalysisForm)

    @Query("SELECT * FROM analysis_form WHERE generalActionId = :generalActionId AND periodType = :periodType ORDER BY beginDate ASC")
    fun getFormsFor(generalActionId: Long, periodType: String): Flow<List<AnalysisForm>>

    @Query("SELECT * FROM analysis_form WHERE generalActionId = :generalActionId AND periodType = :periodType ORDER BY beginDate ASC")
    suspend fun getFormsForOnce(generalActionId: Long, periodType: String): List<AnalysisForm>

    @Insert
    suspend fun insertParam(param: AnalysisFormActionParam): Long

    @Update
    suspend fun updateParam(param: AnalysisFormActionParam)

    @Query("SELECT * FROM analysis_form_action_param WHERE formId = :formId")
    fun getParamsForForm(formId: Long): Flow<List<AnalysisFormActionParam>>

    @Query("DELETE FROM analysis_form_action_param WHERE formId = :formId")
    suspend fun deleteParamsForForm(formId: Long)
}
