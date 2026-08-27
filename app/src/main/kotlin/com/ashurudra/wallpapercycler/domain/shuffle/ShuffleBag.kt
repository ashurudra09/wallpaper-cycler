package com.ashurudra.wallpapercycler.domain.shuffle

import kotlin.random.Random

data class ShuffleBag(
    val sequence: List<String>,
    val index: Int,
    val seed: Long,
) {

    val current: String
        get() = sequence[index]

    fun next(newSeed: Long): ShuffleBag {
        if (index + 1 < sequence.size) {
            return copy(index = index + 1)
        }
        if (sequence.isEmpty()) {
            return copy(seed = newSeed)
        }

        val previousLast = sequence.last()
        var reshuffled = sequence.shuffled(Random(newSeed))
        // Reshuffling can land the same image at both ends of the boundary; only worth
        // fixing up when there's more than one image to swap it against.
        if (sequence.size > 1 && reshuffled[0] == previousLast) {
            val swapWith = Random(newSeed).nextInt(1, reshuffled.size)
            reshuffled = reshuffled.toMutableList().also {
                val tmp = it[0]
                it[0] = it[swapWith]
                it[swapWith] = tmp
            }
        }
        return ShuffleBag(sequence = reshuffled, index = 0, seed = newSeed)
    }

    fun previous(): ShuffleBag? {
        // Never reaches into the previous cycle's order - that was a different shuffle.
        if (index == 0) return null
        return copy(index = index - 1)
    }

    fun reconcile(currentFiles: List<String>): ShuffleBag {
        if (sequence.isEmpty()) {
            return if (currentFiles.isEmpty()) {
                ShuffleBag(sequence = emptyList(), index = 0, seed = seed)
            } else {
                create(currentFiles, seed)
            }
        }

        val played = sequence.subList(0, index + 1)
        val unplayed = sequence.subList(index + 1, sequence.size)

        val currentFileSet = currentFiles.toSet()
        val filteredPlayed = played.filter { it in currentFileSet }
        val filteredUnplayed = unplayed.filter { it in currentFileSet }

        if (filteredPlayed.isEmpty() && filteredUnplayed.isEmpty()) {
            return if (currentFiles.isEmpty()) {
                ShuffleBag(sequence = emptyList(), index = 0, seed = seed)
            } else {
                create(currentFiles, seed)
            }
        }

        val existingIds = filteredPlayed.toSet() + filteredUnplayed.toSet()
        val newIds = currentFiles.filter { it !in existingIds }

        // No fresh seed is handed to reconcile, so the bag's own seed is the only
        // deterministic source of randomness available for placing new arrivals.
        val random = Random(seed)
        val updatedUnplayed = filteredUnplayed.toMutableList()
        for (id in newIds) {
            val position = random.nextInt(updatedUnplayed.size + 1)
            updatedUnplayed.add(position, id)
        }

        val wasCurrentRemoved = played.last() !in currentFileSet

        return when {
            !wasCurrentRemoved -> ShuffleBag(
                sequence = filteredPlayed + updatedUnplayed,
                index = filteredPlayed.size - 1,
                seed = seed,
            )
            updatedUnplayed.isNotEmpty() -> ShuffleBag(
                sequence = filteredPlayed + updatedUnplayed,
                index = filteredPlayed.size,
                seed = seed,
            )
            else -> create(currentFiles, seed)
        }
    }

    companion object {
        fun create(files: List<String>, seed: Long): ShuffleBag {
            return ShuffleBag(sequence = files.shuffled(Random(seed)), index = 0, seed = seed)
        }
    }
}
