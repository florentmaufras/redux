package com.florentmaufras.reduxdemo.universities.data

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class UniversitiesStoreTest {

    @Test
    fun init_shouldNotThrow_withNoneDefault() {
        assertDoesNotThrow {
            UniversitiesStore(
                initialState = UniversitiesState(),
                reducer = UniversitiesReducer(),
                effectHandler = UniversitiesEffectHandler()
            )
        }
    }

    @Test
    fun init_shouldNotThrow_withDefault() {
        assertDoesNotThrow {
            UniversitiesStore()
        }
    }
}