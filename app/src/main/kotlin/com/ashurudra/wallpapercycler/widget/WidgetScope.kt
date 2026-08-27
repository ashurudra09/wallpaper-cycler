package com.ashurudra.wallpapercycler.widget

import com.ashurudra.wallpapercycler.domain.model.ScreenTarget

/**
 * What a placed widget instance follows - picked once at configuration time and stored per
 * App Widget ID (see [com.ashurudra.wallpapercycler.data.prefs.WidgetPreferences]). Unlike a
 * Schedule's own `targets`, this never changes after placement; which underlying schedule it
 * currently resolves to can still change over time as schedules are enabled, disabled, or
 * edited (see [matches]) - mirroring the app's single-owner-per-target model rather than
 * binding the widget to one fixed schedule ID.
 */
enum class WidgetScope {
    HOME, LOCK, BOTH;

    /** True if a schedule with these targets is the one a widget with this scope should show. */
    fun matches(targets: Set<ScreenTarget>): Boolean = when (this) {
        HOME -> ScreenTarget.HOME in targets
        LOCK -> ScreenTarget.LOCK in targets
        BOTH -> ScreenTarget.HOME in targets && ScreenTarget.LOCK in targets
    }
}
