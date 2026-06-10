package com.florentmaufras.redux

import junit.framework.TestCase.assertEquals
import org.junit.Test

class StateOwnerTest {

    data class TestState(val value: Int = 0) : State

    @Test
    fun ownedStateOwner_initialState_isReturned() {
        val owner = OwnedStateOwner(TestState(42))
        assertEquals(TestState(42), owner.currentState)
    }

    @Test
    fun ownedStateOwner_updateState_appliesTransform() {
        val owner = OwnedStateOwner(TestState(0))
        owner.updateState { it.copy(value = it.value + 5) }
        assertEquals(TestState(5), owner.currentState)
    }

    @Test
    fun ownedStateOwner_stateFlow_reflectsUpdate() {
        val owner = OwnedStateOwner(TestState(1))
        owner.updateState { it.copy(value = 99) }
        assertEquals(TestState(99), owner.state.value)
    }
}
