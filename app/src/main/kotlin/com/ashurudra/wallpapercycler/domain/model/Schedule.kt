package com.ashurudra.wallpapercycler.domain.model

/**
 * Phase 2 scope: only the fields the target-conflict logic (TargetArbiter) needs.
 * Phase 3 extends this with the trigger, image source, and shuffle/sort settings once
 * Room entities exist.
 */
data class Schedule(
    val id: String,
    val enabled: Boolean,
    val targets: Set<ScreenTarget>,
)
