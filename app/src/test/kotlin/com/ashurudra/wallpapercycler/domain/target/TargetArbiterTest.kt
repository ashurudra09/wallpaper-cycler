package com.ashurudra.wallpapercycler.domain.target

import com.ashurudra.wallpapercycler.domain.model.Schedule
import com.ashurudra.wallpapercycler.domain.model.ScreenTarget
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetArbiterTest {

    private fun schedule(id: String, enabled: Boolean, vararg targets: ScreenTarget) =
        Schedule(id = id, enabled = enabled, targets = targets.toSet())

    @Test
    fun `partial overlap downgrades the other schedule instead of disabling it`() {
        val a = schedule("a", enabled = true, ScreenTarget.HOME, ScreenTarget.LOCK)
        val b = schedule("b", enabled = false, ScreenTarget.HOME)

        val result = TargetArbiter.resolveEnable(listOf(a, b), "b")

        val newA = result.find { it.id == "a" }!!
        val newB = result.find { it.id == "b" }!!
        assertTrue(newA.enabled)
        assertEquals(setOf(ScreenTarget.LOCK), newA.targets)
        assertTrue(newB.enabled)
        assertEquals(setOf(ScreenTarget.HOME), newB.targets)
    }

    @Test
    fun `full overlap disables the other schedule but leaves its targets untouched`() {
        val a = schedule("a", enabled = true, ScreenTarget.HOME)
        val b = schedule("b", enabled = false, ScreenTarget.HOME)

        val result = TargetArbiter.resolveEnable(listOf(a, b), "b")

        val newA = result.find { it.id == "a" }!!
        val newB = result.find { it.id == "b" }!!
        assertFalse(newA.enabled)
        assertEquals(setOf(ScreenTarget.HOME), newA.targets)
        assertTrue(newB.enabled)
        assertEquals(setOf(ScreenTarget.HOME), newB.targets)
    }

    @Test
    fun `no overlap leaves both schedules enabled unchanged`() {
        val a = schedule("a", enabled = true, ScreenTarget.HOME)
        val b = schedule("b", enabled = false, ScreenTarget.LOCK)

        val result = TargetArbiter.resolveEnable(listOf(a, b), "b")

        val newA = result.find { it.id == "a" }!!
        val newB = result.find { it.id == "b" }!!
        assertTrue(newA.enabled)
        assertEquals(setOf(ScreenTarget.HOME), newA.targets)
        assertTrue(newB.enabled)
        assertEquals(setOf(ScreenTarget.LOCK), newB.targets)
    }

    @Test
    fun `enabling a schedule with no targets disturbs nothing else`() {
        val a = schedule("a", enabled = true, ScreenTarget.HOME, ScreenTarget.LOCK)
        val b = schedule("b", enabled = false)

        val result = TargetArbiter.resolveEnable(listOf(a, b), "b")

        val newA = result.find { it.id == "a" }!!
        val newB = result.find { it.id == "b" }!!
        assertTrue(newA.enabled)
        assertEquals(setOf(ScreenTarget.HOME, ScreenTarget.LOCK), newA.targets)
        assertTrue(newB.enabled)
        assertEquals(emptySet<ScreenTarget>(), newB.targets)
    }

    @Test
    fun `re-enabling an already enabled schedule is idempotent`() {
        val a = schedule("a", enabled = true, ScreenTarget.HOME, ScreenTarget.LOCK)
        val b = schedule("b", enabled = false, ScreenTarget.HOME)

        val firstPass = TargetArbiter.resolveEnable(listOf(a, b), "b")
        val secondPass = TargetArbiter.resolveEnable(firstPass, "b")

        assertEquals(firstPass.toSet(), secondPass.toSet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `enabling an unknown id throws`() {
        val a = schedule("a", enabled = true, ScreenTarget.HOME)

        TargetArbiter.resolveEnable(listOf(a), "missing")
    }

    @Test
    fun `random configurations never end up with two enabled schedules sharing a target`() {
        val random = Random(42)
        val possibleTargets = listOf(
            emptySet(),
            setOf(ScreenTarget.HOME),
            setOf(ScreenTarget.LOCK),
            setOf(ScreenTarget.HOME, ScreenTarget.LOCK),
        )

        repeat(50) {
            // Build a starting list that itself satisfies the invariant, since that's the
            // only kind of state resolveEnable is ever called against in practice.
            var homeClaimed = false
            var lockClaimed = false
            val schedules = (0 until 5).map { index ->
                val targets = possibleTargets[random.nextInt(possibleTargets.size)]
                val wantsEnabled = random.nextBoolean()
                val conflicts = (ScreenTarget.HOME in targets && homeClaimed) ||
                    (ScreenTarget.LOCK in targets && lockClaimed)
                val enabled = wantsEnabled && !conflicts
                if (enabled) {
                    if (ScreenTarget.HOME in targets) homeClaimed = true
                    if (ScreenTarget.LOCK in targets) lockClaimed = true
                }
                schedule("s$index", enabled, *targets.toTypedArray())
            }

            val enablingId = "s${random.nextInt(schedules.size)}"
            val result = TargetArbiter.resolveEnable(schedules, enablingId)

            val enabledSchedules = result.filter { it.enabled }
            assertTrue(enabledSchedules.count { ScreenTarget.HOME in it.targets } <= 1)
            assertTrue(enabledSchedules.count { ScreenTarget.LOCK in it.targets } <= 1)
        }
    }
}
