package com.florentmaufras.reduxdemo.universities.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

@OptIn(ExperimentalCoroutinesApi::class)
class UniversitiesStoreTest {

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_shouldNotThrow_withNoneDefault() {
        assertDoesNotThrow {
            UniversitiesStore(
                application = null,
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
