package com.ashurudra.wallpapercycler.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CycleDao {

    @Query("SELECT * FROM cycle_state WHERE scheduleId = :scheduleId")
    suspend fun getByScheduleId(scheduleId: String): CycleStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cycleState: CycleStateEntity)

    @Query("DELETE FROM cycle_state WHERE scheduleId = :scheduleId")
    suspend fun deleteByScheduleId(scheduleId: String)
}
