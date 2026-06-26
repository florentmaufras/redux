package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge as mergeFlows

class Effect<out Action> private constructor(
    internal val actions: Flow<Action>,
    internal val cancelId: Any? = null,
    internal val cancelInFlight: Boolean = false,
    internal val isCancellation: Boolean = false,
) {
    fun <T> map(transform: (Action) -> T): Effect<T> =
        Effect(actions.map(transform), cancelId, cancelInFlight, isCancellation)

    fun cancellable(id: Any, cancelInFlight: Boolean = false): Effect<Action> =
        Effect(actions, cancelId = id, cancelInFlight = cancelInFlight)

    companion object {
        fun <Action> none(): Effect<Action> = Effect(emptyFlow())

        fun <Action> run(block: suspend FlowCollector<Action>.() -> Unit): Effect<Action> =
            Effect(flow(block))

        fun <Action> send(action: Action): Effect<Action> = Effect(flowOf(action))

        fun <Action> merge(vararg effects: Effect<Action>): Effect<Action> =
            Effect(mergeFlows(*effects.map { it.actions }.toTypedArray()))

        fun <Action> cancel(id: Any): Effect<Action> =
            Effect(emptyFlow(), cancelId = id, isCancellation = true)
    }
}
