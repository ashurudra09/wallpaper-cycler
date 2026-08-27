package com.ashurudra.wallpapercycler.domain.target

import com.ashurudra.wallpapercycler.domain.model.Schedule

object TargetArbiter {

    fun resolveEnable(current: List<Schedule>, enablingId: String): List<Schedule> {
        val enabling = current.find { it.id == enablingId }
            ?: throw IllegalArgumentException("No schedule with id $enablingId in current list")

        return current.map { schedule ->
            when {
                schedule.id == enablingId -> schedule.copy(enabled = true)
                schedule.enabled -> {
                    val remaining = schedule.targets - enabling.targets
                    if (remaining.isNotEmpty()) {
                        schedule.copy(targets = remaining)
                    } else {
                        schedule.copy(enabled = false)
                    }
                }
                else -> schedule
            }
        }
    }
}
