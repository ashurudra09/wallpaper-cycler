package com.ashurudra.wallpapercycler.di

import android.content.Context

/**
 * Manual service locator. Grows with each phase as repositories (Room, DataStore,
 * image sources) are introduced — no DI framework, per the project's tech-stack decision.
 */
class AppContainer(private val appContext: Context)
