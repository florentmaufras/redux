package com.florentmaufras.redux

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StateOwnerTest {

    data class ParentState(val child: ChildState = ChildState())
    data class ChildState(val value: Int = 0)

    @Test
    fun ownedStateOwner_initialState_isReturned() {
        val owner = OwnedStateOwner(ChildState(42))
        assertEquals(ChildState(42), owner.currentState)
    }

    @Test
    fun ownedStateOwner_updateState_appliesTransform() {
        val owner = OwnedStateOwner(ChildState(0))
        owner.updateState { it.copy(value = it.value + 5) }
        assertEquals(ChildState(5), owner.currentState)
    }

    @Test
    fun ownedStateOwner_state_emitsLatestValue() = runTest {
        val owner = OwnedStateOwner(ChildState(1))
        owner.updateState { it.copy(value = 99) }
        assertEquals(ChildState(99), owner.state.first())
    }

    @Test
    fun scopedStateOwner_currentState_readsChildFromParent() {
        val parent = OwnedStateOwner(ParentState(child = ChildState(7)))
        val scoped = ScopedStateOwner(
            parent = parent,
            toChildState = { it.child },
            fromChildState = { p, c -> p.copy(child = c) }
        )
        assertEquals(ChildState(7), scoped.currentState)
    }

    @Test
    fun scopedStateOwner_updateState_writesBackToParent() {
        val parent = OwnedStateOwner(ParentState(child = ChildState(0)))
        val scoped = ScopedStateOwner(
            parent = parent,
            toChildState = { it.child },
            fromChildState = { p, c -> p.copy(child = c) }
        )
        scoped.updateState { it.copy(value = 10) }
        assertEquals(ChildState(10), scoped.currentState)
        assertEquals(ParentState(child = ChildState(10)), parent.currentState)
    }

    @Test
    fun scopedStateOwner_state_emitsCurrentChild() = runTest {
        val parent = OwnedStateOwner(ParentState(child = ChildState(3)))
        val scoped = ScopedStateOwner(
            parent = parent,
            toChildState = { it.child },
            fromChildState = { p, c -> p.copy(child = c) }
        )
        scoped.updateState { it.copy(value = 20) }
        assertEquals(ChildState(20), scoped.state.first())
    }
}
