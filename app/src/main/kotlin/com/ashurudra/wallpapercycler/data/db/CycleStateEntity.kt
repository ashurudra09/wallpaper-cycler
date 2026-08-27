package com.ashurudra.wallpapercycler.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ashurudra.wallpapercycler.domain.shuffle.ShuffleBag

/**
 * One row per schedule (a schedule is one cycle, shared across whichever screens it
 * targets) — persists a [ShuffleBag]'s state between ticks and app restarts.
 */
@Entity(tableName = "cycle_state")
data class CycleStateEntity(
    @PrimaryKey val scheduleId: String,
    val sequence: List<String>,
    val index: Int,
    val seed: Long,
    val consecutiveFailures: Int = 0,
    val lastError: String? = null,
)

fun CycleStateEntity.toDomain(): ShuffleBag = ShuffleBag(sequence = sequence, index = index, seed = seed)

fun ShuffleBag.toEntity(
    scheduleId: String,
    consecutiveFailures: Int = 0,
    lastError: String? = null,
): CycleStateEntity = CycleStateEntity(
    scheduleId = scheduleId,
    sequence = sequence,
    index = index,
    seed = seed,
    consecutiveFailures = consecutiveFailures,
    lastError = lastError,
)
