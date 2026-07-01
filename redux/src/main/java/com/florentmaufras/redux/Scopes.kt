package com.florentmaufras.redux

/**
 * Composes [child] into this parent reducer over [scope]: the parent reduces first
 * (and may observe the child action), then the child reduces its slice; the child's
 * effect is lifted to parent actions via [Scope.embedChildAction] and merged.
 */
fun <ParentState, ParentAction, ChildState, ChildAction> Reducer<ParentState, ParentAction>.scope(
    scope: Scope<ParentState, ParentAction, ChildState, ChildAction>,
    child: Reducer<ChildState, ChildAction>,
): Reducer<ParentState, ParentAction> = Reducer { state, action ->
    val parent = this.reduce(state, action)            // parent runs first / observes the action
    val childAction = scope.toChildAction(action)
        ?: return@Reducer parent
    val childResult = child.reduce(scope.toChildState(parent.state), childAction)
    ReduceResult(
        scope.fromChildState(parent.state, childResult.state),
        Effect.merge(parent.effect, childResult.effect.map(scope.embedChildAction)),
    )
}

/**
 * Composes [child] over a dynamic collection of identified elements. The parent reduces
 * first (so it handles add / remove / broadcast actions and can observe element actions);
 * then, if the action is element-addressed (via [ForEachScope.toChildAction]), the matching
 * element is reduced and written back, and its effect is lifted to parent actions via
 * [ForEachScope.embedChildAction] and merged.
 *
 * An action for an id not present in the collection is a no-op. Elements keep their order.
 * Cancellation is not auto-namespaced: a child keys its cancellable effects on its own
 * `state.id`, and the parent emits `Effect.cancel(id)` when it removes an element.
 *
 * Because ids are not namespaced, element ids must be unique across the whole store, not
 * just within this collection — the cancel-id namespace is store-global (see
 * [Effect.cancellable]). Prefer a distinct id type per collection, or a composite key such
 * as `state.id to "tick"`, so an element's effect never collides with a parent effect or
 * another collection's element.
 */
fun <ParentState, ParentAction, ChildState, ChildAction, Id> Reducer<ParentState, ParentAction>.forEach(
    scope: ForEachScope<ParentState, ParentAction, ChildState, ChildAction, Id>,
    child: Reducer<ChildState, ChildAction>,
): Reducer<ParentState, ParentAction> where ChildState : Identifiable<Id> = Reducer { state, action ->
    val parent = this.reduce(state, action)                    // parent first
    val (id, childAction) = scope.toChildAction(action) ?: return@Reducer parent
    val children = scope.toChildren(parent.state)
    val index = children.indexOfFirst { it.id == id }
    if (index < 0) return@Reducer parent                       // unknown/removed element → no-op
    val childResult = child.reduce(children[index], childAction)
    val updated = children.toMutableList().also { it[index] = childResult.state }
    ReduceResult(
        scope.fromChildren(parent.state, updated),
        Effect.merge(parent.effect, childResult.effect.map { scope.embedChildAction(id, it) }),
    )
}
