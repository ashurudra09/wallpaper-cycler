package com.ashurudra.wallpapercycler.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ScheduleEntity::class, CycleStateEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao
    abstract fun cycleDao(): CycleDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wallpaper_cycler.db",
                )
                    // Pre-release app, no installed base to migrate - wipes local dev
                    // data on schema change instead of writing a migration path.
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
    }
}
