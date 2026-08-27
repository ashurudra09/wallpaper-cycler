package com.ashurudra.wallpapercycler.ui.schedules

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashurudra.wallpapercycler.data.db.AppDatabase
import com.ashurudra.wallpapercycler.data.db.toDomain
import com.ashurudra.wallpapercycler.domain.model.Schedule
import com.ashurudra.wallpapercycler.domain.schedule.nextTriggerAt
import com.ashurudra.wallpapercycler.domain.usecase.ApplyOutcome
import com.ashurudra.wallpapercycler.domain.usecase.ApplyWallpaperUseCase
import com.ashurudra.wallpapercycler.domain.usecase.DeleteScheduleUseCase
import com.ashurudra.wallpapercycler.domain.usecase.ToggleScheduleUseCase
import com.ashurudra.wallpapercycler.domain.usecase.ToggleScheduleUseCase.ToggleResult
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the schedule list, enriched with what the raw [Schedule] alone can't tell you. */
data class ScheduleCardState(
    val schedule: Schedule,
    val currentImageUri: Uri?,
    val nextImageUri: Uri?,
    val nextChangeLabel: String,
    val cycleErrorMessage: String?,
)

data class UiState(
    val cards: List<ScheduleCardState>,
    val isLoading: Boolean,
)

private const val TICKER_INTERVAL_MILLIS = 30_000L

/**
 * Backs the schedule list (the app home screen). Combines the live `schedules` table with a
 * 30-second ticker so the next-change countdown label keeps advancing even on a run with no
 * other database write, and enriches every row with a peek at its current/next image.
 *
 * Actions (toggle/next/previous/delete) all write to Room tables that [uiState] observes
 * (via ScheduleDao.observeAll, indirectly touched by peek/apply through cycle_state), so the
 * list recomputes on its own after any action — no manual refresh call is needed.
 */
class SchedulesViewModel(private val context: Context) : ViewModel() {

    private val database = AppDatabase.getInstance(context)
    private val applyWallpaperUseCase = ApplyWallpaperUseCase(context)

    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICKER_INTERVAL_MILLIS)
        }
    }

    // One-off UI events (a rejected-enable error, or an informational note like "already at
    // the start of this cycle"). A Channel rather than a StateFlow so each message is
    // delivered — and shown as a Snackbar — exactly once, never replayed to a new collector.
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val uiState: StateFlow<UiState> = combine(
        database.scheduleDao().observeAll(),
        database.cycleDao().observeAll(),
        ticker,
    ) { scheduleEntities, _, _ -> scheduleEntities }
        .map { scheduleEntities ->
            val now = Instant.now()
            val cards = scheduleEntities.map { entity ->
                val schedule = entity.toDomain()
                val nextInstant = nextTriggerAt(schedule.trigger, schedule.anchoredAt, now, ZoneId.systemDefault())
                val peek = applyWallpaperUseCase.peek(schedule.id)
                ScheduleCardState(
                    schedule = schedule,
                    currentImageUri = peek.current?.uri,
                    nextImageUri = peek.next?.uri,
                    nextChangeLabel = formatNextChangeLabel(nextInstant, now),
                    cycleErrorMessage = peek.errorMessage,
                )
            }
            UiState(cards = cards, isLoading = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState(cards = emptyList(), isLoading = true),
        )

    fun onToggle(scheduleId: String, enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                val result = ToggleScheduleUseCase(context).enable(scheduleId)
                if (result is ToggleResult.Rejected) {
                    _messages.send(result.reason)
                }
            } else {
                ToggleScheduleUseCase(context).disable(scheduleId)
            }
        }
    }

    fun onNext(scheduleId: String) {
        viewModelScope.launch {
            applyWallpaperUseCase.applyNext(scheduleId)
        }
    }

    fun onPrevious(scheduleId: String) {
        viewModelScope.launch {
            val result = applyWallpaperUseCase.applyPrevious(scheduleId)
            if (result.outcome is ApplyOutcome.AtCycleStart) {
                _messages.send("Already at the start of this cycle")
            }
        }
    }

    fun onDelete(scheduleId: String, deleteManagedSetFiles: Boolean) {
        viewModelScope.launch {
            DeleteScheduleUseCase(context).delete(scheduleId, deleteManagedSetFiles)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { SchedulesViewModel(context.applicationContext) }
        }
    }
}

private fun formatNextChangeLabel(instant: Instant?, now: Instant): String {
    if (instant == null) return "Not scheduled"
    val duration = Duration.between(now, instant)
    if (duration.isNegative || duration.isZero) return "Any moment now"
    val minutes = duration.toMinutes()
    return when {
        minutes < 1 -> "in under a minute"
        minutes < 60 -> "in $minutes min"
        duration.toHours() < 24 -> "in ${duration.toHours()} h"
        else -> "in ${duration.toDays()} d"
    }
}
