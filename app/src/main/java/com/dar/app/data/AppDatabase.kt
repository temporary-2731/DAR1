package com.dar.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Dsla::class,
        ActionEntity::class,
        GeneralActionEntity::class,
        GeneralActionActionCrossRef::class,
        SuperActionEntity::class,
        SuperActionGeneralCrossRef::class,
        RecordingRow::class,
        AnalysisForm::class,
        AnalysisFormActionParam::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dslaDao(): DslaDao
    abstract fun actionDao(): ActionDao
    abstract fun generalActionDao(): GeneralActionDao
    abstract fun superActionDao(): SuperActionDao
    abstract fun recordingDao(): RecordingDao
    abstract fun analysisFormDao(): AnalysisFormDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dar_database"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
