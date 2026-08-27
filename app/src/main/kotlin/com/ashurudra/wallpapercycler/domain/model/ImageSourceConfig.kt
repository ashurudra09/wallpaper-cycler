package com.ashurudra.wallpapercycler.domain.model

/** A schedule's source is exactly one of these — never both, never neither. */
sealed interface ImageSourceConfig {
    data class LinkedFolder(val treeUri: String) : ImageSourceConfig
    data class ManagedSet(val setId: String) : ImageSourceConfig
}
