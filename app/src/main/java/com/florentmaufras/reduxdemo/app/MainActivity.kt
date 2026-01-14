package com.florentmaufras.reduxdemo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.florentmaufras.reduxdemo.universities.ui.UniversitiesScreen

class MainActivity: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UniversitiesScreen()
        }
    }
}