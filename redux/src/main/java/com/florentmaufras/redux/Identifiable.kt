package com.florentmaufras.redux

/**
 * Stable identity for an element addressed within a collection composed by [forEach].
 * Two elements are the same element across reductions iff their [id] is equal; ids in a
 * collection must be unique.
 */
interface Identifiable<out Id> {
    val id: Id
}
