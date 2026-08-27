package com.ashurudra.wallpapercycler.domain.usecase

import android.content.Context
import com.ashurudra.wallpapercycler.data.db.AppDatabase
import com.ashurudra.wallpapercycler.data.db.CycleStateEntity
import com.ashurudra.wallpapercycler.data.db.ScheduleEntity
import com.ashurudra.wallpapercycler.data.db.toDomain
import com.ashurudra.wallpapercycler.data.db.toEntity
import com.ashurudra.wallpapercycler.data.source.ImageRef
import com.ashurudra.wallpapercycler.data.source.sortedFor
import com.ashurudra.wallpapercycler.data.source.toImageSource
import com.ashurudra.wallpapercycler.domain.shuffle.ShuffleBag
import com.ashurudra.wallpapercycler.domain.shuffle.SortedCycle
import com.ashurudra.wallpapercycler.wallpaper.WallpaperApplier
import com.ashurudra.wallpapercycler.widget.WidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ApplyOutcome {
    data class Success(val imageName: String) : ApplyOutcome
    data object Empty : ApplyOutcome
    data object AtCycleStart : ApplyOutcome
    data class Error(val message: String) : ApplyOutcome
}

data class ApplyResult(val outcome: ApplyOutcome, val autoDisabled: Boolean)

/**
 * Read-only view of what [ApplyWallpaperUseCase.applyNext] would do next, for UI display
 * (schedule card "current"/"next" thumbnails) - never applies a wallpaper or persists
 * anything. [errorMessage] is set (with both refs null) when the source can't be read or
 * has no images; [current]/[next] can individually be null if the cycle state points at an
 * id no longer present in the source, without that being a hard error.
 */
data class CyclePeek(val current: ImageRef?, val next: ImageRef?, val errorMessage: String?)

private const val MAX_CONSECUTIVE_FAILURES = 5

/**
 * Advances one schedule by one image and applies it. Reused by both the automatic Worker
 * (applyNext) and the future manual Next/Previous UI (applyNext/applyPrevious) - alarm
 * rescheduling is deliberately NOT done here, only in the Worker, so a manual tap never
 * resets the automatic countdown.
 */
class ApplyWallpaperUseCase(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val wallpaperApplier = WallpaperApplier(context)

    suspend fun applyNext(scheduleId: String): ApplyResult =
        advance(scheduleId, Direction.NEXT).also { WidgetRefresher.requestUpdateAll(context) }

    suspend fun applyPrevious(scheduleId: String): ApplyResult =
        advance(scheduleId, Direction.PREVIOUS).also { WidgetRefresher.requestUpdateAll(context) }

    /**
     * Pure simulation of one more [applyNext] step, for the schedule card's current/next
     * thumbnails. Mirrors [advance]'s branching exactly so "current" means the same thing
     * here as it does there, but never calls the wallpaper applier and never writes to
     * [com.ashurudra.wallpapercycler.data.db.CycleDao].
     */
    suspend fun peek(scheduleId: String): CyclePeek {
        val scheduleEntity = database.scheduleDao().getById(scheduleId)
            ?: return CyclePeek(current = null, next = null, errorMessage = "schedule not found")
        val schedule = scheduleEntity.toDomain()

        val images = try {
            withContext(Dispatchers.IO) { schedule.source.toImageSource(context).listImages() }
        } catch (e: Exception) {
            return CyclePeek(current = null, next = null, errorMessage = e.message ?: e.javaClass.simpleName)
        }
        if (images.isEmpty()) {
            return CyclePeek(current = null, next = null, errorMessage = "no images found")
        }

        val cycleState = database.cycleDao().getByScheduleId(scheduleId)

        val currentId: String?
        val nextId: String?
        if (schedule.shuffleEnabled) {
            val ids = images.map { it.id }
            val bag = (cycleState?.toDomain() ?: ShuffleBag.create(ids, System.currentTimeMillis())).reconcile(ids)
            currentId = bag.current
            // Reuses the bag's own persisted seed rather than a fresh time-based one: peek()
            // never persists this simulated reshuffle, so a fresh seed here would make the
            // "next" preview change on every call that lands on a cycle boundary, even with
            // nothing about the schedule's actual state having advanced (visible as a widget
            // thumbnail that flickers between different images on repeated redraws). The real
            // reshuffle in advance() still uses a genuinely fresh seed when it actually runs.
            nextId = bag.next(newSeed = bag.seed).current
        } else {
            val sortedIds = images.sortedFor(schedule.sortOrder).map { it.id }
            val currentIndex = SortedCycle.reconcileIndex(cycleState?.sequence?.getOrNull(cycleState.index), sortedIds)
            currentId = sortedIds.getOrNull(currentIndex)
            nextId = sortedIds.getOrNull(SortedCycle.nextIndex(currentIndex, sortedIds.size))
        }

        val currentRef = images.find { it.id == currentId }
        val nextRef = images.find { it.id == nextId }
        return CyclePeek(current = currentRef, next = nextRef, errorMessage = null)
    }

    private enum class Direction { NEXT, PREVIOUS }

    private suspend fun advance(scheduleId: String, direction: Direction): ApplyResult {
        val scheduleEntity = database.scheduleDao().getById(scheduleId)
            ?: return ApplyResult(ApplyOutcome.Error("schedule not found"), autoDisabled = false)
        val cycleState = database.cycleDao().getByScheduleId(scheduleId)

        // Cycle state computed before a possible failure further down, so a decode/apply
        // exception on one image still advances past it rather than persisting nothing and
        // getting stuck retrying the same image forever.
        var pendingCycleState: CycleStateEntity? = null

        return try {
            val schedule = scheduleEntity.toDomain()
            val images = withContext(Dispatchers.IO) {
                schedule.source.toImageSource(context).listImages()
            }
            if (images.isEmpty()) {
                return recordFailure(scheduleEntity, cycleState, pendingCycleState, ApplyOutcome.Empty, "no images found")
            }

            val currentId: String?
            if (schedule.shuffleEnabled) {
                val ids = images.map { it.id }
                val bag = (cycleState?.toDomain() ?: ShuffleBag.create(ids, System.currentTimeMillis())).reconcile(ids)
                val newBag = when (direction) {
                    Direction.NEXT -> bag.next(newSeed = System.currentTimeMillis())
                    Direction.PREVIOUS -> bag.previous()
                        ?: return ApplyResult(ApplyOutcome.AtCycleStart, autoDisabled = false)
                }
                currentId = newBag.current
                pendingCycleState = newBag.toEntity(scheduleId)
            } else {
                val sortedIds = images.sortedFor(schedule.sortOrder).map { it.id }
                val currentIndex = SortedCycle.reconcileIndex(cycleState?.sequence?.getOrNull(cycleState.index), sortedIds)
                val newIndex = when (direction) {
                    Direction.NEXT -> SortedCycle.nextIndex(currentIndex, sortedIds.size)
                    Direction.PREVIOUS -> SortedCycle.previousIndex(currentIndex, sortedIds.size)
                }
                currentId = sortedIds.getOrNull(newIndex)
                pendingCycleState = CycleStateEntity(scheduleId = scheduleId, sequence = sortedIds, index = newIndex, seed = 0L)
            }

            if (currentId == null) {
                return recordFailure(scheduleEntity, cycleState, pendingCycleState, ApplyOutcome.Empty, "no images found")
            }

            val imageRef = images.first { it.id == currentId }
            withContext(Dispatchers.IO) {
                wallpaperApplier.apply(imageRef.uri, schedule.fitMode, schedule.targets)
            }

            database.cycleDao().upsert(pendingCycleState)
            ApplyResult(ApplyOutcome.Success(imageName = imageRef.displayName), autoDisabled = false)
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            recordFailure(scheduleEntity, cycleState, pendingCycleState, ApplyOutcome.Error(message), message)
        }
    }

    private suspend fun recordFailure(
        scheduleEntity: ScheduleEntity,
        cycleState: CycleStateEntity?,
        pendingCycleState: CycleStateEntity?,
        outcome: ApplyOutcome,
        errorMessage: String,
    ): ApplyResult {
        val newConsecutiveFailures = (cycleState?.consecutiveFailures ?: 0) + 1
        val newCycleState = (pendingCycleState ?: cycleState)
            ?.copy(consecutiveFailures = newConsecutiveFailures, lastError = errorMessage)
            ?: CycleStateEntity(
                scheduleId = scheduleEntity.id,
                sequence = emptyList(),
                index = 0,
                seed = 0L,
                consecutiveFailures = newConsecutiveFailures,
                lastError = errorMessage,
            )
        database.cycleDao().upsert(newCycleState)

        val autoDisabled = newConsecutiveFailures >= MAX_CONSECUTIVE_FAILURES
        if (autoDisabled) {
            database.scheduleDao().upsert(scheduleEntity.copy(enabled = false))
        }
        return ApplyResult(outcome, autoDisabled)
    }
}
