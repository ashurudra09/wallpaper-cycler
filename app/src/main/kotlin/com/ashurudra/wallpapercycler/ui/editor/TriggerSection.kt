package com.ashurudra.wallpapercycler.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ashurudra.wallpapercycler.domain.model.Trigger
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class TriggerMode { INTERVAL, TIMES_OF_DAY }

private enum class IntervalUnit(val label: String, val millisPerUnit: Long) {
    MINUTES("Minutes", 60_000L),
    HOURS("Hours", 3_600_000L),
    DAYS("Days", 86_400_000L),
}

private const val MIN_INTERVAL_MILLIS = 60_000L
private const val MAX_INTERVAL_MILLIS = 7 * 24 * 60 * 60_000L // 604_800_000 - 7 days
private const val MAX_TIMES_OF_DAY = 8

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

private fun deriveAmountAndUnit(millis: Long): Pair<String, IntervalUnit> = when {
    millis > 0 && millis % IntervalUnit.DAYS.millisPerUnit == 0L ->
        (millis / IntervalUnit.DAYS.millisPerUnit).toString() to IntervalUnit.DAYS
    millis > 0 && millis % IntervalUnit.HOURS.millisPerUnit == 0L ->
        (millis / IntervalUnit.HOURS.millisPerUnit).toString() to IntervalUnit.HOURS
    else ->
        (millis / IntervalUnit.MINUTES.millisPerUnit).coerceAtLeast(0L).toString() to IntervalUnit.MINUTES
}

/**
 * The trigger step: a segmented Interval / Times-of-day choice, each side with its own inputs.
 *
 * All editable state (amount/unit, the times list, the day-of-week set) is hoisted to the top
 * of this composable rather than derived from [initialTrigger] on every recomposition - it is
 * seeded ONCE from [initialTrigger] and from then on is the single source of truth, pushed
 * outward via [onIntervalChange]/[onTimesOfDayChange]. This is what lets the user flip back and
 * forth between the two modes within one visit to this screen without losing what they typed
 * on the other side, and avoids a feedback loop where a value just pushed out gets immediately
 * re-derived and stomps mid-typing input. Callers are expected to only compose this once the
 * real starting trigger is known (e.g. after an existing schedule finishes loading).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerSection(
    initialTrigger: Trigger,
    onIntervalChange: (Long) -> Unit,
    onTimesOfDayChange: (List<LocalTime>, Set<DayOfWeek>) -> Unit,
) {
    var mode by remember {
        mutableStateOf(if (initialTrigger is Trigger.TimesOfDay) TriggerMode.TIMES_OF_DAY else TriggerMode.INTERVAL)
    }

    val initialAmountAndUnit = remember {
        deriveAmountAndUnit((initialTrigger as? Trigger.Interval)?.everyMillis ?: DEFAULT_INTERVAL_MILLIS)
    }
    var amountText by remember { mutableStateOf(initialAmountAndUnit.first) }
    var unit by remember { mutableStateOf(initialAmountAndUnit.second) }

    var times by remember {
        mutableStateOf((initialTrigger as? Trigger.TimesOfDay)?.times.orEmpty().sorted())
    }
    var days by remember {
        mutableStateOf((initialTrigger as? Trigger.TimesOfDay)?.daysOfWeek.orEmpty())
    }
    var showTimePicker by remember { mutableStateOf(false) }

    val amountLong = amountText.toLongOrNull()
    val intervalMillis = (amountLong ?: 0L) * unit.millisPerUnit
    val intervalOutOfRange = amountLong == null || intervalMillis !in MIN_INTERVAL_MILLIS..MAX_INTERVAL_MILLIS

    LaunchedEffect(amountText, unit) {
        if (mode == TriggerMode.INTERVAL) onIntervalChange(intervalMillis)
    }
    LaunchedEffect(times, days) {
        if (mode == TriggerMode.TIMES_OF_DAY) onTimesOfDayChange(times, days)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == TriggerMode.INTERVAL,
                onClick = { mode = TriggerMode.INTERVAL; onIntervalChange(intervalMillis) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Interval") }
            SegmentedButton(
                selected = mode == TriggerMode.TIMES_OF_DAY,
                onClick = { mode = TriggerMode.TIMES_OF_DAY; onTimesOfDayChange(times, days) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Times of day") }
        }

        if (mode == TriggerMode.INTERVAL) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Every", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { new -> if (new.length <= 6 && new.all(Char::isDigit)) amountText = new },
                    modifier = Modifier.width(90.dp),
                    singleLine = true,
                    isError = intervalOutOfRange,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                IntervalUnit.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = unit == option,
                        onClick = { unit = option },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = IntervalUnit.entries.size),
                    ) { Text(option.label) }
                }
            }
            if (intervalOutOfRange) {
                Text(
                    "Must be between 1 minute and 7 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (times.isEmpty()) {
                    Text(
                        "No times added yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                times.forEach { time ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(time.format(TIME_FORMATTER), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { times = times.filterNot { it == time } }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove time")
                        }
                    }
                }
                Button(onClick = { showTimePicker = true }, enabled = times.size < MAX_TIMES_OF_DAY) {
                    Text(if (times.size >= MAX_TIMES_OF_DAY) "Maximum of 8 times" else "Add time")
                }

                Text("Repeat on", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DayOfWeek.values().toList()) { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                if (days.isEmpty() || times.isEmpty()) {
                    Text(
                        "Add at least one time and one day before this schedule can be enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    times = (times + LocalTime.of(timePickerState.hour, timePickerState.minute)).distinct().sorted()
                    showTimePicker = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timePickerState) },
        )
    }
}
