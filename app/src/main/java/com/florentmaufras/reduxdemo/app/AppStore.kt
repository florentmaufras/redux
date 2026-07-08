package com.florentmaufras.reduxdemo.app

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import com.florentmaufras.redux.Reducer
import com.florentmaufras.redux.Store
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import com.florentmaufras.reduxdemo.universities.data.UniversitiesReducer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private fun defaultRetrofit(): Retrofit = Retrofit.Builder()
    .baseUrl("http://universities.hipolabs.com")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

class AppStore(
    application: Application? = null,
    initialState: AppState = AppState(),
    override val reducer: Reducer<AppState, AppAction> = appReducer(
        UniversitiesReducer(
            universitiesService = UniversitiesService(defaultRetrofit()),
            openUrl = { url ->
                application?.startActivity(
                    Intent(Intent.ACTION_VIEW, url.toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        )
    ),
) : Store<AppState, AppAction>(initialState)
