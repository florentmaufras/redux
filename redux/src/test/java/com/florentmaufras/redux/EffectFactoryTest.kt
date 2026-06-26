package com.florentmaufras.redux

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EffectFactoryTest {

    // Test-only: flatten an effect tree to the actions it would emit (ignoring cancellation).
    private fun <A> Effect<A>.allActions(): Flow<A> = when (this) {
        Effect.None, is Effect.Cancel -> emptyFlow()
        is Effect.Actions -> flow
        is Effect.Cancellable -> effect.allActions()
        is Effect.Merge -> merge(*effects.map { it.allActions() }.toTypedArray())
    }

    @Test
    fun none_emitsNothing() = runTest {
        assertEquals(emptyList<Int>(), Effect.none<Int>().allActions().toList())
    }

    @Test
    fun send_emitsSingleAction() = runTest {
        assertEquals(listOf(7), Effect.send(7).allActions().toList())
    }

    @Test
    fun run_emitsWhatBlockEmits() = runTest {
        val effect = Effect.run<Int> { emit(1); emit(2) }
        assertEquals(listOf(1, 2), effect.allActions().toList())
    }

    @Test
    fun map_transformsEmittedActions() = runTest {
        assertEquals(listOf(2), Effect.send(1).map { it + 1 }.allActions().toList())
    }

    @Test
    fun merge_emitsFromAllEffects() = runTest {
        val merged = Effect.merge(Effect.send(1), Effect.send(2)).allActions().toList()
        assertEquals(setOf(1, 2), merged.toSet())
    }

    @Test
    fun cancellable_wrapsEffectWithId() {
        val effect = Effect.send(1).cancellable("id", cancelInFlight = true)
        assertTrue(effect is Effect.Cancellable)
        effect as Effect.Cancellable
        assertEquals("id", effect.id)
        assertEquals(true, effect.cancelInFlight)
        assertTrue(effect.effect is Effect.Actions)
    }

    @Test
    fun cancel_isCancelNodeWithId() {
        val effect = Effect.cancel<Int>("id")
        assertTrue(effect is Effect.Cancel)
        assertEquals("id", (effect as Effect.Cancel).id)
    }

    @Test
    fun map_preservesCancellableWrapper() {
        val effect = Effect.send(1).cancellable("x", cancelInFlight = true).map { it + 1 }
        assertTrue(effect is Effect.Cancellable)
        effect as Effect.Cancellable
        assertEquals("x", effect.id)
        assertEquals(true, effect.cancelInFlight)
    }

    @Test
    fun merge_preservesEachBranchCancellable() {
        val merged = Effect.merge(Effect.send(1).cancellable("x"), Effect.send(2))
        assertTrue(merged is Effect.Merge)
        merged as Effect.Merge
        assertTrue(merged.effects[0] is Effect.Cancellable)
        assertEquals("x", (merged.effects[0] as Effect.Cancellable).id)
        assertTrue(merged.effects[1] is Effect.Actions)
    }
}
