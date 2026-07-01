package com.florentmaufras.redux

/**
 * Lenses/prisms mapping a parent feature to a collection of identified children, used by
 * [forEach]. Symmetric with [Scope], but the action prism carries the element [Id].
 *
 * - [toChildren] / [fromChildren]: read and write the child collection in parent state.
 * - [toChildAction]: extract an element-addressed action as `(id, childAction)`, or `null`
 *   when the action does not target an element (e.g. a broadcast or add/remove action).
 * - [embedChildAction]: embed a child action for element [Id] back into a parent action.
 */
interface ForEachScope<ParentState, ParentAction, ChildState, ChildAction, Id>
    where ChildState : Identifiable<Id> {
    val toChildren: (ParentState) -> List<ChildState>
    val fromChildren: (ParentState, List<ChildState>) -> ParentState
    val toChildAction: (ParentAction) -> Pair<Id, ChildAction>?
    val embedChildAction: (Id, ChildAction) -> ParentAction
}
