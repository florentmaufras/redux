package com.florentmaufras.reduxdemo.universities.data

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import com.florentmaufras.redux.OwnedStateOwner
import com.florentmaufras.redux.StateOwner
import com.florentmaufras.redux.Store
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private fun defaultRetrofit(): Retrofit = Retrofit.Builder()
    .baseUrl("http://universities.hipolabs.com")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

class UniversitiesStore(
    application: Application? = null,
    stateOwner: StateOwner<UniversitiesState> = OwnedStateOwner(UniversitiesState()),
    override val reducer: UniversitiesReducer = UniversitiesReducer(),
    private val effectHandler: UniversitiesEffectHandler = UniversitiesEffectHandler(
        universitiesService = UniversitiesService(defaultRetrofit()),
        openUrl = { url ->
            application?.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    )
) : Store<UniversitiesAction, UniversitiesState, UniversitiesEffect>(stateOwner) {

    override fun handleEffect(effect: UniversitiesEffect, cancelId: String?) {
        launchEffect(effect, cancelId) { effectHandler.handle(it) }
    }

    init {
        if (state.value.countrySearched.isNotBlank()) {
            dispatch(UniversitiesAction.LoadUniversities(state.value.countrySearched))
        }
    }
}
