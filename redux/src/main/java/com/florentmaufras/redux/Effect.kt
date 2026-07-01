package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * A unit of asynchronous work, parameterized by the action it feeds back into the
 * store. Effects form a small tree: leaf [Actions] produced by [run]/[send],
 * combined with [merge], scoped for cancellation with [cancellable], lifted with
 * [map], and stopped with [cancel]. The store interprets the tree; nothing else
 * needs to know its shape.
 */
sealed class Effect<out Action> {

    internal data object None : Effect<Nothing>()

    internal class Actions<out Action>(val flow: Flow<Action>) : Effect<Action>()

    internal class Cancellable<out Action>(
        val id: Any,
        val cancelInFlight: Boolean,
        val effect: Effect<Action>,
    ) : Effect<Action>()

    internal class Cancel(val id: Any) : Effect<Nothing>()

    internal class Merge<out Action>(val effects: List<Effect<Action>>) : Effect<Action>()

    fun <T> map(transform: (Action) -> T): Effect<T> = when (this) {
        None -> None
        is Actions -> Actions(flow.map(transform))
        is Cancellable -> Cancellable(id, cancelInFlight, effect.map(transform))
        is Cancel -> this
        is Merge -> Merge(effects.map { it.map(transform) })
    }

    /**
     * Tags this effect so the store tracks it under [id] and can [cancel] it. Ids share
     * one store-global namespace across every composed feature, so prefer distinct types
     * (an `enum`/`object`) over bare strings to avoid cross-feature collisions. Effects
     * sharing an [id] with `cancelInFlight = false` run concurrently; passing `true`
     * cancels any in-flight effect(s) under [id] before this one starts.
     */
    fun cancellable(id: Any, cancelInFlight: Boolean = false): Effect<Action> =
        Cancellable(id, cancelInFlight, this)

    companion object {
        fun <Action> none(): Effect<Action> = None

        /**
         * Runs [block] as asynchronous work, emitting actions back into the store. The
         * block must handle its own failures (e.g. wrap calls in try/catch and emit an
         * error action); an uncaught exception escapes to the coroutine's uncaught handler.
         */
        fun <Action> run(block: suspend FlowCollector<Action>.() -> Unit): Effect<Action> =
            Actions(flow(block))

        fun <Action> send(action: Action): Effect<Action> = Actions(flowOf(action))

        fun <Action> merge(vararg effects: Effect<Action>): Effect<Action> =
            Merge(effects.toList())

        /** Cancels every in-flight effect tagged with [id] via [cancellable]. */
        fun <Action> cancel(id: Any): Effect<Action> = Cancel(id)
    }
}
