package com.florentmaufras.reduxdemo.universities.data

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class UniversityTest {

    @Test
    fun init_shouldNotThrow_withValues() {
        assertDoesNotThrow {
            University(
                "",
                arrayListOf(),
                "",
                "",
                arrayListOf(),
                ""
            )
        }
    }

    @Test
    fun init_shouldNotThrow_withNullOnOptional() {
        assertDoesNotThrow {
            University(
                "",
                null,
                null,
                "",
                arrayListOf(),
                ""
            )
        }
    }
}