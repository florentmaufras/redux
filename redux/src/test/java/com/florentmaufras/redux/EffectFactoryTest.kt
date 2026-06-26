package com.florentmaufras.redux

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EffectFactoryTest {

    @Test
    fun none_emitsNothing() = runTest {
        assertEquals(emptyList<Int>(), Effect.none<Int>().actions.toList())
    }

    @Test
    fun send_emitsSingleAction() = runTest {
        assertEquals(listOf(7), Effect.send(7).actions.toList())
    }

    @Test
    fun run_emitsWhatBlockEmits() = runTest {
        val effect = Effect.run<Int> { emit(1); emit(2) }
        assertEquals(listOf(1, 2), effect.actions.toList())
    }

    @Test
    fun map_transformsEmittedActions() = runTest {
        assertEquals(listOf(2), Effect.send(1).map { it + 1 }.actions.toList())
    }

    @Test
    fun merge_emitsFromAllEffects() = runTest {
        val merged = Effect.merge(Effect.send(1), Effect.send(2)).actions.toList()
        assertEquals(setOf(1, 2), merged.toSet())
    }

    @Test
    fun cancellable_tagsCancelId() {
        val effect = Effect.send(1).cancellable("id", cancelInFlight = true)
        assertEquals("id", effect.cancelId)
        assertEquals(true, effect.cancelInFlight)
    }

    @Test
    fun cancel_marksCancellationForId() {
        val effect = Effect.cancel<Int>("id")
        assertEquals("id", effect.cancelId)
        assertEquals(true, effect.isCancellation)
    }
}
